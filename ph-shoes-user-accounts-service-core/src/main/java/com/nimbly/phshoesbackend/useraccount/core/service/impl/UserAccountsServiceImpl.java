package com.nimbly.phshoesbackend.useraccount.core.service.impl;

import com.auth0.jwt.interfaces.DecodedJWT;
import com.nimbly.phshoesbackend.useraccount.core.model.Account;
import com.nimbly.phshoesbackend.useraccount.core.repository.AccountRepository;
import com.nimbly.phshoesbackend.commons.core.security.EmailCrypto;
import com.nimbly.phshoesbackend.commons.core.security.jwt.JwtTokenService;
import com.nimbly.phshoesbackend.commons.core.security.jwt.JwtVerificationException;
import com.nimbly.phshoesbackend.useraccount.core.exception.InvalidCredentialsException;
import com.nimbly.phshoesbackend.useraccount.core.service.UserAccountsService;
import com.nimbly.phshoesbackend.useraccount.core.util.SensitiveValueMasker;
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
    private final EmailCrypto emailCrypto;
    private final JwtTokenService jwtTokenService;
    private final PasswordEncoder passwordEncoder;

    @Override
    public CreateUserAccountResponse register(CreateUserAccountRequest request) {
        String normalized = emailCrypto.normalize(request.getEmail());
        if (normalized == null || normalized.isBlank()) {
            throw new IllegalArgumentException("email must not be blank");
        }

        List<String> emailHashes = emailCrypto.hashCandidates(normalized);
        if (emailHashes == null || emailHashes.isEmpty()) {
            emailHashes = List.of(emailCrypto.hash(normalized));
        }
        emailHashes = List.copyOf(emailHashes);
        String primaryHash = emailHashes.get(0);
        log.info("accounts.register attempt emailHashPrefix={}", SensitiveValueMasker.hashPrefix(primaryHash));

        boolean exists = emailHashes.stream().anyMatch(accountRepository::existsByEmailHash);
        if (exists) {
            log.warn("accounts.register duplicate emailHashPrefix={}", SensitiveValueMasker.hashPrefix(primaryHash));
            throw new IllegalStateException("Email already exists.");
        }

        String password = request.getPassword();
        if (password == null || password.isBlank()) {
            throw new IllegalArgumentException("password must not be blank");
        }
        Instant now = Instant.now();
        Account account = new Account();
        account.setUserId(UUID.randomUUID().toString());
        account.setEmailHash(primaryHash);
        account.setEmailEnc(emailCrypto.encrypt(normalized));
        account.setPasswordHash(passwordEncoder.encode(password));
        account.setIsVerified(false);
        account.setCreatedAt(now);
        account.setUpdatedAt(now);
        accountRepository.save(account);

        log.info("account register created userId={} emailHashPrefix={}", account.getUserId(), SensitiveValueMasker.hashPrefix(primaryHash));
        CreateUserAccountResponse response = new CreateUserAccountResponse();
        response.setUserid(account.getUserId());
        response.setEmailVerified(Boolean.FALSE);
        response.setEmail(account.getEmailHash());
        return response;
    }

    @Override
    public TokenContentResponse getContentFromToken(String authorizationHeader) {
        String token = authorizationHeader == null
                ? null
                : (authorizationHeader.startsWith("Bearer ")
                    ? authorizationHeader.substring(7).trim()
                    : authorizationHeader.trim());
        if (token == null || token.isBlank()) {
            throw new InvalidCredentialsException();
        }
        DecodedJWT jwt;
        try {
            jwt = jwtTokenService.parseAccess(token);
        } catch (JwtVerificationException ex) {
            log.error("user.accounts Invalid Credentials: {}", ex.getMessage());
            throw new InvalidCredentialsException();
        }

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

}

