package com.nimbly.phshoesbackend.useraccount.service.impl;

import com.nimbly.phshoesbackend.useraccount.auth.JwtTokenProvider;
import com.nimbly.phshoesbackend.useraccount.auth.exception.InvalidCredentialsException;
import com.nimbly.phshoesbackend.useraccount.exception.EmailAlreadyRegisteredException;
import com.nimbly.phshoesbackend.useraccount.model.AccountAttrs;
import com.nimbly.phshoesbackend.useraccount.model.dto.AccountCreateRequest;
import com.nimbly.phshoesbackend.useraccount.model.dto.AccountResponse;
import com.nimbly.phshoesbackend.useraccount.model.dto.GetContentFromTokenResponse;
import com.nimbly.phshoesbackend.useraccount.repository.AccountRepository;
import com.nimbly.phshoesbackend.useraccount.security.HashingUtil;
import com.nimbly.phshoesbackend.useraccount.service.UserAccountsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;

import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserAccountsServiceImpl implements UserAccountsService {

    private final DynamoDbClient ddb;
    private final BCryptPasswordEncoder passwordEncoder;
    @Autowired
    private final AccountRepository accountRepository;
    private final JwtTokenProvider jwtTokenProvider;

    @Override
    public AccountResponse register(AccountCreateRequest req) {
        final String emailRaw  = req.getEmail().trim();
        final String emailNorm = emailRaw.toLowerCase(Locale.ROOT);
        final String emailHash = HashingUtil.sha256Hex(emailNorm);

        if (existsByEmailHash(emailHash)) {
            throw new EmailAlreadyRegisteredException();
        }

        final String userId = UUID.randomUUID().toString();
        final Instant now = Instant.now();

        var item = Map.<String, AttributeValue>of(
                AccountAttrs.PK_USERID,      AttributeValue.builder().s(userId).build(),
                AccountAttrs.EMAIL_HASH,     AttributeValue.builder().s(emailHash).build(),
                AccountAttrs.PASSWORD_HASH,  AttributeValue.builder().s(passwordEncoder.encode(req.getPassword())).build(),
                AccountAttrs.IS_VERIFIED,    AttributeValue.builder().bool(false).build(),
                AccountAttrs.CREATED_AT,     AttributeValue.builder().s(now.toString()).build(),
                AccountAttrs.UPDATED_AT,     AttributeValue.builder().s(now.toString()).build()
        );

        ddb.putItem(PutItemRequest.builder()
                .tableName(AccountAttrs.TABLE)
                .item(item)
                .conditionExpression("attribute_not_exists(#pk)")
                .expressionAttributeNames(Map.of("#pk", AccountAttrs.PK_USERID))
                .build());

        // Controller will orchestrate verification create+send after this returns.
        return AccountResponse.builder()
                .userid(userId)
                .email(emailRaw)
                .isVerified(false)
                .createdAt(now.toString())
                .updatedAt(now.toString())
                .build();
    }

    @Override
    public void deleteOwnAccount(String userId) {
        log.info("accounts.delete start userId={}", userId);
        accountRepository.revokeAllSessionsForUser(userId);
        accountRepository.deleteById(userId);
        log.info("accounts.delete success userId={}", userId);
    }

    @Override
    public GetContentFromTokenResponse getContentFromTokenBearer(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            throw new InvalidCredentialsException();
        }
        String token = authorizationHeader.substring(7).trim();
        var jwt = jwtTokenProvider.parseAccess(token);
        String email = jwt.getClaim("email").asString();
        return new GetContentFromTokenResponse(email == null ? "" : email);
    }

    private boolean existsByEmailHash(String emailHash) {
        var r = ddb.query(QueryRequest.builder()
                .tableName(AccountAttrs.TABLE)
                .indexName(AccountAttrs.GSI_EMAIL)
                .keyConditionExpression("#e = :eh")
                .expressionAttributeNames(Map.of("#e", AccountAttrs.EMAIL_HASH))
                .expressionAttributeValues(Map.of(":eh", AttributeValue.builder().s(emailHash).build()))
                .limit(1)
                .build());
        return r.count() != null && r.count() > 0;
    }
}
