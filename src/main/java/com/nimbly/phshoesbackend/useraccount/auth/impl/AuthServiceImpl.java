package com.nimbly.phshoesbackend.useraccount.auth.impl;

import com.nimbly.phshoesbackend.useraccount.auth.AuthService;
import com.nimbly.phshoesbackend.useraccount.auth.JwtTokenProvider;
import com.nimbly.phshoesbackend.useraccount.auth.dto.LoginRequest;
import com.nimbly.phshoesbackend.useraccount.auth.dto.TokenResponse;
import com.nimbly.phshoesbackend.useraccount.auth.exception.AccountLockedException;
import com.nimbly.phshoesbackend.useraccount.auth.exception.InvalidCredentialsException;
import com.nimbly.phshoesbackend.useraccount.config.AppAuthProps;
import com.nimbly.phshoesbackend.useraccount.config.LockoutProps;
import com.nimbly.phshoesbackend.useraccount.model.Account;
import com.nimbly.phshoesbackend.useraccount.repository.AccountRepository;   // <-- use repo
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final AccountRepository accounts;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final AppAuthProps authProps;
    private final LockoutProps lockProps;

    /** Precomputed dummy hash to equalize timing when email does not exist. */
    private String dummyHash;

    @jakarta.annotation.PostConstruct
    void init() {
        // Generate a dummy BCrypt hash at runtime (same cost as real check)
        this.dummyHash = passwordEncoder.encode("dummy-password-for-timing");
    }

    @Override
    public TokenResponse login(LoginRequest request, String ip, String userAgent) {
        long t0 = System.currentTimeMillis();
        final String email = normalizeEmail(request.getEmail());
        final String rawPassword = Objects.requireNonNullElse(request.getPassword(), "");

        try {
            log.info("auth.login start email={} ip={}", maskEmail(email), ip);

            final Optional<Account> opt = accounts.findByEmail(email);

            if (opt.isEmpty()) {
                log.warn("auth.login no_account emailHash={}", sha256Hex(email));
            }

            if (opt.isPresent() && isLocked(opt.get())) {
                log.warn("auth.login locked userId={} emailHash={}", opt.get().getUserid(), sha256Hex(email));
                throw new AccountLockedException();
            }

            final boolean passwordOk;
            if (opt.isPresent()) {
                String stored = opt.get().getPassword();
                if (stored == null || stored.isBlank()) {
                    log.warn("auth.login stored_password_missing userId={} emailHash={}",
                            opt.get().getUserid(), sha256Hex(email));
                    passwordOk = false;
                } else {
                    passwordOk = passwordEncoder.matches(rawPassword, stored);
                    log.debug("auth.login password_match userId={} match={}",
                            opt.get().getUserid(), passwordOk);
                }
            } else {
                // burn cycles to hide user existence
                passwordEncoder.matches(rawPassword, dummyHash);
                passwordOk = false;
            }

            if (!passwordOk) {
                accounts.recordFailedLogin(email, lockProps.getMaxFailures(), lockProps.getDurationSeconds());
                log.warn("auth.login failed reason={} emailHash={}",
                        (opt.isPresent() ? "bad_password" : "no_account"), sha256Hex(email));
                throw new InvalidCredentialsException();
            }

            final Account acc = opt.get();
            accounts.recordSuccessfulLogin(acc.getUserid(), ip, userAgent);

            final String token = jwtTokenProvider.issueAccessToken(acc.getUserid(), email);

            TokenResponse res = new TokenResponse();
            res.setAccess_token(token);
            res.setExpires_in(authProps.getAccessTtlSeconds());

            log.info("auth.login success userId={} inMs={}", acc.getUserid(), System.currentTimeMillis() - t0);
            return res;

        } catch (RuntimeException e) {
            log.error("auth.login error emailHash={} ip={} ua={} msg={}",
                    sha256Hex(email), ip, truncate(userAgent, 64), e.toString(), e);
            throw e; // let the GlobalExceptionHandler convert to response
        }
    }

    @Override
    public void logout(String authorizationHeader) {
        String token = null;
        if (authorizationHeader != null && authorizationHeader.startsWith("Bearer ")) {
            token = authorizationHeader.substring(7);
        }
        log.info("auth.logout tokenPresent={}", token != null);
    }

    private static String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase();
    }

    private static boolean isLocked(Account acc) {
        Long until = acc.getLockUntil();
        return until != null && until > System.currentTimeMillis();
    }


    private static String maskEmail(String email) {
        if (email == null || email.isBlank()) return "(blank)";
        int at = email.indexOf('@');
        if (at <= 1) return "***" + (at >= 0 ? email.substring(at) : "");
        return email.charAt(0) + "***" + (at >= 0 ? email.substring(at) : "");
    }

    private static String truncate(String s, int max) {
        if (s == null) return "unknown";
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }

    private static String sha256Hex(String s) {
        try {
            var md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] d = md.digest((s == null ? "" : s).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            var sb = new StringBuilder(d.length * 2);
            for (byte b : d) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception ex) { return "sha256-error"; }
    }
}
