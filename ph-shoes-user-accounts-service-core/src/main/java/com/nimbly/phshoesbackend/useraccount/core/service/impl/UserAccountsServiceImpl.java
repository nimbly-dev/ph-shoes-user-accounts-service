package com.nimbly.phshoesbackend.useraccount.core.service.impl;

import com.auth0.jwt.interfaces.DecodedJWT;
import com.nimbly.phshoesbackend.useraccount.core.model.Account;
import com.nimbly.phshoesbackend.useraccount.core.repository.AccountRepository;
import com.nimbly.phshoesbackend.commons.core.repository.SuppressionRepository;
import com.nimbly.phshoesbackend.useraccount.core.repository.VerificationRepository;
import com.nimbly.phshoesbackend.commons.core.security.EmailCrypto;
import com.nimbly.phshoesbackend.commons.core.security.jwt.JwtTokenService;
import com.nimbly.phshoesbackend.commons.core.security.jwt.JwtVerificationException;
import com.nimbly.phshoesbackend.useraccount.core.auth.exception.InvalidCredentialsException;
import com.nimbly.phshoesbackend.useraccount.core.service.UserAccountsService;
import com.nimbly.phshoesbackend.useraccounts.model.CreateUserAccountRequest;
import com.nimbly.phshoesbackend.useraccounts.model.CreateUserAccountResponse;
import com.nimbly.phshoesbackend.useraccounts.model.TokenContentResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserAccountsServiceImpl implements UserAccountsService {

    private final AccountRepository accountRepository;
    private final SuppressionRepository suppressionRepository;
    private final VerificationRepository verificationRepository;
    private final EmailCrypto emailCrypto;
    private final JwtTokenService jwtTokenService;
    private final PasswordEncoder passwordEncoder;

    /** Workflow: normalize email -> guard rails -> persist -> respond. */
    @Override
    public CreateUserAccountResponse register(CreateUserAccountRequest request) {
        String normalized = normalizeEmailOrThrow(request.getEmail());
        List<String> emailHashes = ensureHashes(normalized);
        String primaryHash = emailHashes.get(0);
        log.info("accounts.register attempt emailHashPrefix={}", shortHash(primaryHash));

        enforceEmailNotTaken(emailHashes);

        Account account = buildAccount(request, normalized, primaryHash);
        accountRepository.save(account);

        log.info("Test");
        log.info("account register created userId={} emailHashPrefix={}", account.getUserId(), shortHash(primaryHash));
        return toCreateResponse(account);
    }

    @Override
    public TokenContentResponse getContentFromToken(String authorizationHeader) {
        String token = extractBearer(authorizationHeader);
        DecodedJWT jwt = parseOrThrow(token);

        String sub = jwt.getSubject();
        String email = jwt.getClaim("email").asString();
        Long iat = jwt.getIssuedAt() == null ? null : jwt.getIssuedAt().toInstant().getEpochSecond();
        Long exp = jwt.getExpiresAt() == null ? null : jwt.getExpiresAt().toInstant().getEpochSecond();
        List<String> roles = Optional.ofNullable(jwt.getClaim("roles"))
                .filter(claim -> !claim.isNull())
                .map(claim -> claim.asList(String.class))
                .map(List::copyOf)
                .orElseGet(List::of);

        TokenContentResponse out = new TokenContentResponse();
        out.setSub(sub);
        out.setEmail(email);
        out.setRoles(List.copyOf(roles));
        out.setIat(iat);
        out.setExp(exp);
        return out;
    }

    @Override
    public void deleteOwnAccount(String userId) {
        accountRepository.deleteByUserId(userId);
        log.info("accounts.delete userId={}", userId);
    }

    private static String extractBearer(String header) {
        if (header == null) return null;
        return header.startsWith("Bearer ") ? header.substring(7).trim() : header.trim();
    }

    private String normalizeEmailOrThrow(String email) {
        String normalized = emailCrypto.normalize(email);
        if (normalized == null || normalized.isBlank()) {
            throw new IllegalArgumentException("email must not be blank");
        }
        return normalized;
    }

    private List<String> ensureHashes(String normalized) {
        List<String> candidates = emailCrypto.hashCandidates(normalized);
        if (candidates == null || candidates.isEmpty()) {
            return List.of(emailCrypto.hash(normalized));
        }
        return List.copyOf(candidates);
    }


    private void enforceEmailNotTaken(List<String> hashes) {
        boolean exists = hashes.stream().anyMatch(accountRepository::existsByEmailHash);
        if (exists) {
            log.warn("accounts.register duplicate emailHashPrefix={}", shortHash(hashes.get(0)));
            throw new IllegalStateException("Email already exists.");
        }
    }

    private Account buildAccount(CreateUserAccountRequest request, String normalizedEmail, String emailHash) {
        Instant now = Instant.now();
        Account account = new Account();
        account.setUserId(UUID.randomUUID().toString());
        account.setEmailHash(emailHash);
        account.setEmailEnc(emailCrypto.encrypt(normalizedEmail));
        account.setPasswordHash(passwordEncoder.encode(requirePassword(request.getPassword())));
        account.setIsVerified(false);
        account.setCreatedAt(now);
        account.setUpdatedAt(now);
        return account;
    }

    private String requirePassword(String password) {
        if (password == null || password.isBlank()) {
            throw new IllegalArgumentException("password must not be blank");
        }
        return password;
    }

    private static CreateUserAccountResponse toCreateResponse(Account account) {
        var resp = new CreateUserAccountResponse();
        resp.setUserid(account.getUserId());
        resp.setEmailVerified(Boolean.FALSE);
        resp.setEmail(account.getEmailHash());
        return resp;
    }

    private static String shortHash(String hash) {
        if (hash == null || hash.isBlank()) {
            return "(blank)";
        }
        return hash.length() <= 8 ? hash : hash.substring(0, 8);
    }

    private DecodedJWT parseOrThrow(String token) {
        if (token == null || token.isBlank()) {
            throw new InvalidCredentialsException();
        }
        try {
            return jwtTokenService.parseAccess(token);
        } catch (JwtVerificationException ex) {
            throw new InvalidCredentialsException();
        }
    }
}
