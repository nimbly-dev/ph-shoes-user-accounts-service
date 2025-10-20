package com.nimbly.phshoesbackend.useraccount.auth.impl;

import com.nimbly.phshoesbackend.services.common.core.model.Account;
import com.nimbly.phshoesbackend.services.common.core.repository.AccountRepository;
import com.nimbly.phshoesbackend.useraccount.auth.AuthService;
import com.nimbly.phshoesbackend.useraccount.auth.JwtTokenProvider;
import com.nimbly.phshoesbackend.useraccount.auth.dto.LoginRequest;
import com.nimbly.phshoesbackend.useraccount.auth.dto.TokenResponse;
import com.nimbly.phshoesbackend.useraccount.auth.exception.AccountLockedException;
import com.nimbly.phshoesbackend.useraccount.auth.exception.InvalidCredentialsException;
import com.nimbly.phshoesbackend.useraccount.config.props.AppAuthProps;
import com.nimbly.phshoesbackend.useraccount.config.props.LockoutProps;
import com.nimbly.phshoesbackend.useraccount.exception.EmailNotVerifiedException;
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

    private String dummyHash;

    @jakarta.annotation.PostConstruct
    void init() {
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

            // lockout check (if account exists)
            if (opt.isPresent() && isLocked(opt.get())) {
                log.warn("auth.login locked userId={} emailHash={}", opt.get().getUserid(), sha256Hex(email));
                throw new AccountLockedException();
            }

            // password verification (burn cycles if user unknown)
            final boolean passwordOk;
            if (opt.isPresent()) {
                String stored = opt.get().getPassword();
                if (stored == null || stored.isBlank()) {
                    log.warn("auth.login stored_password_missing userId={} emailHash={}",
                            opt.get().getUserid(), sha256Hex(email));
                    passwordOk = false;
                } else {
                    passwordOk = passwordEncoder.matches(rawPassword, stored);
                    log.debug("auth.login password_match userId={} match={}", opt.get().getUserid(), passwordOk);
                }
            } else {
                passwordEncoder.matches(rawPassword, dummyHash); // equalize timing
                passwordOk = false;
            }

            if (!passwordOk) {
                accounts.recordFailedLogin(email, lockProps.getMaxFailures(), lockProps.getDurationSeconds());
                log.warn("auth.login failed reason={} emailHash={}",
                        (opt.isPresent() ? "bad_password" : "no_account"), sha256Hex(email));
                throw new InvalidCredentialsException();
            }

            // success path
            final Account acc = opt.get();

            // BLOCK unverified users
            Boolean verified = acc.getEmailVerified();
            if (verified == null || !verified) {
                log.warn("auth.login unverified userId={} emailHash={}", acc.getUserid(), sha256Hex(email));
                throw new EmailNotVerifiedException();
            }

            // record telemetry & reset counters
            accounts.recordSuccessfulLogin(acc.getUserid(), ip, userAgent);

            // issue token
            final String token = jwtTokenProvider.issueAccessToken(acc.getUserid(), email);

            // create session from token (requires jti + exp in token)
            var decoded = jwtTokenProvider.parseAccess(token);
            String jti = decoded.getId();
            if (jti == null || jti.isBlank() || decoded.getExpiresAt() == null) {
                log.error("auth.login token_missing_jti_or_exp userId={}", acc.getUserid());
                throw new IllegalStateException("Token missing jti/exp");
            }
            long exp = decoded.getExpiresAt().toInstant().getEpochSecond();
            accounts.createSession(jti, acc.getUserid(), exp, ip, userAgent);

            // response
            TokenResponse res = new TokenResponse();
            res.setAccess_token(token);
            res.setExpires_in(authProps.getAccessTtlSeconds());

            log.info("auth.login success userId={} inMs={}", acc.getUserid(), System.currentTimeMillis() - t0);
            return res;

        } catch (RuntimeException e) {
            log.error("auth.login error emailHash={} ip={} ua={} msg={}",
                    sha256Hex(email), ip, truncate(userAgent, 64), e.toString(), e);
            throw e; // GlobalExceptionHandler will format the response
        }
    }


    @Override
    public void logout(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            log.warn("auth.logout missing_or_bad_authorization_header");
            throw new InvalidCredentialsException(); // -> 401
        }

        String token = authorizationHeader.substring(7).trim();
        var jwt = jwtTokenProvider.parseAccess(token); // throws on invalid/expired

        String jti = jwt.getId();
        if (jti == null || jti.isBlank()) {
            log.warn("auth.logout missing_jti");
            throw new InvalidCredentialsException();
        }

        // Second logout or unknown session -> 401
        if (!accounts.isSessionActive(jti)) {
            log.info("auth.logout not_active jti={}", jti);
            throw new InvalidCredentialsException();
        }

        // First logout -> revoke and return 204
        accounts.revokeSession(jti);
        log.info("auth.logout revoked jti={} sub={}", jti, jwt.getSubject());
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
