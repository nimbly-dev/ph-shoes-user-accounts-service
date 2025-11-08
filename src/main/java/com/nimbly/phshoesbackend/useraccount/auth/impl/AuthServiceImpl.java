package com.nimbly.phshoesbackend.useraccount.auth.impl;

import com.nimbly.phshoesbackend.services.common.core.model.Account;
import com.nimbly.phshoesbackend.services.common.core.repository.AccountRepository;
import com.nimbly.phshoesbackend.services.common.core.repository.SessionRepository;
import com.nimbly.phshoesbackend.services.common.core.repository.VerificationRepository;
import com.nimbly.phshoesbackend.useraccount.auth.AuthService;
import com.nimbly.phshoesbackend.useraccount.auth.JwtTokenProvider;
import com.nimbly.phshoesbackend.useraccount.auth.exception.AccountLockedException;
import com.nimbly.phshoesbackend.useraccount.auth.exception.InvalidCredentialsException;
import com.nimbly.phshoesbackend.useraccount.config.props.AppAuthProps;
import com.nimbly.phshoesbackend.useraccount.config.props.LockoutProps;
import com.nimbly.phshoesbackend.useraccount.exception.EmailNotVerifiedException;
import com.nimbly.phshoesbackend.useraccount.security.EmailCrypto;
import com.nimbly.phshoesbackend.useraccounts.model.LoginRequest;
import com.nimbly.phshoesbackend.useraccounts.model.TokenResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
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
    private final EmailCrypto emailCrypto;
    private final SessionRepository sessionRepository;
    private final VerificationRepository verificationRepository;

    @Override
    public TokenResponse login(LoginRequest request, String ip, String userAgent) {
        long t0 = System.currentTimeMillis();

        final String normalizedEmail = emailCrypto.normalize(request.getEmail());
        if (normalizedEmail == null || normalizedEmail.isBlank()) {
            throw new InvalidCredentialsException();
        }
        final List<String> emailHashes = emailCrypto.hashCandidates(normalizedEmail);
        final String primaryHash = emailHashes.isEmpty() ? null : emailHashes.get(0);
        final String rawPassword = Objects.requireNonNullElse(request.getPassword(), "");

        try {
            log.info("auth.login start emailHashPrefix={} ip={}", shortHash(primaryHash), ip);

            Optional<Account> opt = Optional.empty();
            for (String candidate : emailHashes) {
                opt = accounts.findByEmailHash(candidate);
                if (opt.isPresent()) {
                    break;
                }
            }
            if (opt.isEmpty()) {
                log.warn("auth.login no_account emailHashPrefix={}", shortHash(primaryHash));
                throw new InvalidCredentialsException();
            }

            final Account acc = opt.get();

            if (isLocked(acc)) {
                log.warn("auth.login locked userId={} emailHashPrefix={}", acc.getUserId(), shortHash(primaryHash));
                throw new AccountLockedException();
            }

            String stored = acc.getPasswordHash();
            boolean passwordOk = (stored != null && !stored.isBlank()) && passwordEncoder.matches(rawPassword, stored);
            if (!passwordOk) {
                recordFailedLogin(acc);
                log.warn("auth.login failed reason=bad_password userId={} emailHashPrefix={}", acc.getUserId(), shortHash(primaryHash));
                throw new InvalidCredentialsException();
            }

            if (primaryHash != null && !primaryHash.equals(acc.getEmailHash())) {
                migrateEmailHash(acc, primaryHash);
            }

            if (Boolean.FALSE.equals(acc.getIsVerified())) {
                if (hasVerifiedEmail(emailHashes)) {
                    accounts.setVerified(acc.getUserId(), true);
                    acc.setIsVerified(true);
                    log.info("auth.login restored_verification userId={} emailHashPrefix={}", acc.getUserId(), shortHash(primaryHash));
                }
            }

            if (Boolean.FALSE.equals(acc.getIsVerified())) {
                log.warn("auth.login unverified userId={} emailHashPrefix={}", acc.getUserId(), shortHash(primaryHash));
                throw new EmailNotVerifiedException();
            }

            recordSuccessfulLogin(acc, ip, userAgent);

            final String token = jwtTokenProvider.issueAccessToken(acc.getUserId(), normalizedEmail);
            var decoded = jwtTokenProvider.parseAccess(token);
            String jti = decoded.getId();
            if (jti == null || jti.isBlank() || decoded.getExpiresAt() == null) {
                log.error("auth.login token_missing_jti_or_exp userId={}", acc.getUserId());
                throw new IllegalStateException("Token missing jti/exp");
            }
            long exp = decoded.getExpiresAt().toInstant().getEpochSecond();
            sessionRepository.createSession(jti, acc.getUserId(), exp, ip, userAgent);

            TokenResponse res = new TokenResponse();
            res.setAccessToken(token);
            res.setExpiresIn((long) authProps.getAccessTtlSeconds());

            log.info("auth.login success userId={} inMs={}", acc.getUserId(), System.currentTimeMillis() - t0);
            return res;

        } catch (RuntimeException e) {
            log.error("auth.login error emailHashPrefix={} ip={} ua={} msg={}",
                    shortHash(primaryHash), ip, truncate(userAgent, 64), e.toString(), e);
            throw e;
        }
    }

    @Override
    public void logout(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            log.warn("auth.logout missing_or_bad_authorization_header");
            throw new InvalidCredentialsException();
        }
        String token = authorizationHeader.substring(7).trim();
        var jwt = jwtTokenProvider.parseAccess(token);

        String jti = jwt.getId();
        if (jti == null || jti.isBlank()) {
            log.warn("auth.logout missing_jti");
            throw new InvalidCredentialsException();
        }

        if (!sessionRepository.isSessionActive(jti)) throw new InvalidCredentialsException();
        sessionRepository.revokeSession(jti);

        log.info("auth.logout revoked jti={} sub={}", jti, jwt.getSubject());
    }

    private boolean isLocked(Account acc) {
        Instant until = acc.getLockUntil();
        return until != null && Instant.now().isBefore(until);
    }

    private void recordFailedLogin(Account acc) {
        Integer failures = acc.getLoginFailCount();
        int newCount = (failures == null ? 0 : failures) + 1;
        acc.setLoginFailCount(newCount);
        if (newCount >= lockProps.getMaxFailures()) {
            acc.setLockUntil(Instant.now().plusSeconds(lockProps.getDurationSeconds()));
            acc.setLoginFailCount(0);
        }
        acc.setUpdatedAt(Instant.now());
        accounts.save(acc);
    }

    private void recordSuccessfulLogin(Account acc, String ip, String userAgent) {
        acc.setLoginFailCount(0);
        acc.setLockUntil(null);
        acc.setLastLoginAt(Instant.now());
        acc.setLastLoginIp(ip);
        acc.setLastLoginUserAgent(userAgent);
        acc.setUpdatedAt(Instant.now());
        accounts.save(acc);
    }

    private static String shortHash(String emailHash) {
        if (emailHash == null) return "(null)";
        return emailHash.length() <= 8 ? emailHash : emailHash.substring(0, 8);
    }

    private static String truncate(String s, int max) {
        if (s == null) return "unknown";
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }

    private void migrateEmailHash(Account acc, String newHash) {
        acc.setEmailHash(newHash);
        acc.setUpdatedAt(Instant.now());
        accounts.save(acc);
    }

    private boolean hasVerifiedEmail(List<String> emailHashes) {
        for (String candidate : emailHashes) {
            if (verificationRepository.hasVerifiedEntryForEmailHash(candidate)) {
                return true;
            }
        }
        return false;
    }
}
