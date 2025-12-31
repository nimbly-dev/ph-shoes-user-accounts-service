package com.nimbly.phshoesbackend.useraccount.core.auth.impl;

import com.auth0.jwt.interfaces.DecodedJWT;
import com.nimbly.phshoesbackend.useraccount.core.auth.AuthService;
import com.nimbly.phshoesbackend.useraccount.core.exception.AccountLockedException;
import com.nimbly.phshoesbackend.useraccount.core.exception.InvalidCredentialsException;
import com.nimbly.phshoesbackend.useraccount.core.config.props.LockoutProps;
import com.nimbly.phshoesbackend.useraccount.core.model.Account;
import com.nimbly.phshoesbackend.useraccount.core.repository.AccountRepository;
import com.nimbly.phshoesbackend.useraccount.core.repository.SessionRepository;
import com.nimbly.phshoesbackend.useraccount.core.repository.VerificationRepository;
import com.nimbly.phshoesbackend.useraccount.core.exception.EmailNotVerifiedException;
import com.nimbly.phshoesbackend.commons.core.security.EmailCrypto;
import com.nimbly.phshoesbackend.commons.core.security.jwt.JwtTokenService;
import com.nimbly.phshoesbackend.commons.core.security.jwt.JwtVerificationException;
import com.nimbly.phshoesbackend.useraccounts.model.LoginRequest;
import com.nimbly.phshoesbackend.useraccounts.model.TokenResponse;
import com.nimbly.phshoesbackend.useraccount.core.util.SensitiveValueMasker;
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
    private final JwtTokenService jwtTokenService;
    private final LockoutProps lockProps;
    private final EmailCrypto emailCrypto;
    private final SessionRepository sessionRepository;
    private final VerificationRepository verificationRepository;

    @Override
    /**
     * Authenticates a user by normalized email and password, enforcing lockout and verification rules,
     * then issues an access token and records the session metadata.
     */
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
            log.info("auth.login start emailHashPrefix={} ip={}", SensitiveValueMasker.hashPrefix(primaryHash), ip);

            Optional<Account> opt = accounts.findByAnyEmailHash(emailHashes);
            if (opt.isEmpty()) {
                log.warn("auth.login no_account emailHashPrefix={}", SensitiveValueMasker.hashPrefix(primaryHash));
                throw new InvalidCredentialsException();
            }

            final Account acc = opt.get();

            Instant lockUntil = acc.getLockUntil();
            if (lockUntil != null && Instant.now().isBefore(lockUntil)) {
                log.warn("auth.login locked userId={} emailHashPrefix={}", acc.getUserId(), SensitiveValueMasker.hashPrefix(primaryHash));
                throw new AccountLockedException();
            }

            String stored = acc.getPasswordHash();
            boolean passwordOk = (stored != null && !stored.isBlank()) && passwordEncoder.matches(rawPassword, stored);
            if (!passwordOk) {
                Integer failures = acc.getLoginFailCount();
                int newCount = (failures == null ? 0 : failures) + 1;
                acc.setLoginFailCount(newCount);
                if (newCount >= lockProps.getMaxFailures()) {
                    acc.setLockUntil(Instant.now().plusSeconds(lockProps.getDurationSeconds()));
                    acc.setLoginFailCount(0);
                }
                acc.setUpdatedAt(Instant.now());
                accounts.save(acc);
                log.warn("auth.login failed reason=bad_password userId={} emailHashPrefix={}", acc.getUserId(), SensitiveValueMasker.hashPrefix(primaryHash));
                throw new InvalidCredentialsException();
            }

            if (primaryHash != null && !primaryHash.equals(acc.getEmailHash())) {
                acc.setEmailHash(primaryHash);
                acc.setUpdatedAt(Instant.now());
                accounts.save(acc);
            }

            if (Boolean.FALSE.equals(acc.getIsVerified())) {
                boolean verifiedByHash = false;
                for (String candidate : emailHashes) {
                    if (verificationRepository.hasVerifiedEntryForEmailHash(candidate)) {
                        verifiedByHash = true;
                        break;
                    }
                }
                if (verifiedByHash) {
                    accounts.setVerified(acc.getUserId(), true);
                    acc.setIsVerified(true);
                    log.info("auth.login restored_verification userId={} emailHashPrefix={}", acc.getUserId(), SensitiveValueMasker.hashPrefix(primaryHash));
                }
            }

            if (Boolean.FALSE.equals(acc.getIsVerified())) {
                log.warn("auth.login unverified userId={} emailHashPrefix={}", acc.getUserId(), SensitiveValueMasker.hashPrefix(primaryHash));
                throw new EmailNotVerifiedException();
            }

            acc.setLoginFailCount(0);
            acc.setLockUntil(null);
            acc.setLastLoginAt(Instant.now());
            acc.setLastLoginIp(ip);
            acc.setLastLoginUserAgent(userAgent);
            acc.setUpdatedAt(Instant.now());
            accounts.save(acc);

            final String token = jwtTokenService.issueAccessToken(acc.getUserId(), normalizedEmail);
            DecodedJWT decoded = jwtTokenService.parseAccess(token);
            String jti = decoded.getId();
            if (jti == null || jti.isBlank() || decoded.getExpiresAt() == null) {
                log.error("auth.login token_missing_jti_or_exp userId={}", acc.getUserId());
                throw new IllegalStateException("Token missing jti/exp");
            }
            long exp = decoded.getExpiresAt().toInstant().getEpochSecond();
            sessionRepository.createSession(jti, acc.getUserId(), exp, ip, userAgent);

            TokenResponse res = new TokenResponse();
            res.setAccessToken(token);
            res.setExpiresIn((long) jwtTokenService.getAccessTtlSeconds());

            log.info("auth.login success userId={} inMs={}", acc.getUserId(), System.currentTimeMillis() - t0);
            return res;

        } catch (RuntimeException e) {
            log.error("auth.login error emailHashPrefix={} ip={} ua={} msg={}",
                    SensitiveValueMasker.hashPrefix(primaryHash), ip, SensitiveValueMasker.truncateForLog(userAgent, 64), e.toString(), e);
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
        DecodedJWT jwt;
        try {
            jwt = jwtTokenService.parseAccess(token);
        } catch (JwtVerificationException ex) {
            throw new InvalidCredentialsException();
        }

        String jti = jwt.getId();
        if (jti == null || jti.isBlank()) {
            log.warn("auth.logout missing_jti");
            throw new InvalidCredentialsException();
        }

        if (!sessionRepository.isSessionActive(jti)) {
            throw new InvalidCredentialsException();
        }
        sessionRepository.revokeSession(jti);

        log.info("auth.logout revoked jti={} sub={}", jti, jwt.getSubject());
    }

}


