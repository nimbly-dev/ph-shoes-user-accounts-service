package com.nimbly.phshoesbackend.useraccount.verification.impl;

import com.nimbly.phshoesbackend.useraccount.config.props.AppVerificationProps;
import com.nimbly.phshoesbackend.useraccount.exception.InvalidVerificationTokenException;
import com.nimbly.phshoesbackend.useraccount.exception.VerificationAlreadyUsedException;
import com.nimbly.phshoesbackend.useraccount.exception.VerificationExpiredException;
import com.nimbly.phshoesbackend.useraccount.exception.VerificationNotFoundException;
import com.nimbly.phshoesbackend.useraccount.model.AccountAttrs;
import com.nimbly.phshoesbackend.useraccount.model.ResolvedEmail;
import com.nimbly.phshoesbackend.useraccount.model.VerificationAttrs;
import com.nimbly.phshoesbackend.useraccount.model.VerificationEntry;
import com.nimbly.phshoesbackend.useraccount.model.dto.AccountResponse;
import com.nimbly.phshoesbackend.useraccount.repository.VerificationRepository;
import com.nimbly.phshoesbackend.useraccount.security.HashingUtil;
import com.nimbly.phshoesbackend.useraccount.service.NotificationService;
import com.nimbly.phshoesbackend.useraccount.verification.VerificationService;
import com.nimbly.phshoesbackend.useraccount.verification.VerificationTokenCodec;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItem;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItemsRequest;
import software.amazon.awssdk.services.dynamodb.model.Update;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class VerificationServiceImpl implements VerificationService {

    @Autowired
    private final VerificationRepository repo;
    private final VerificationTokenCodec codec;
    private final NotificationService notifier;
    private final AppVerificationProps vprops;

    private final DynamoDbClient ddb;
    private final BCryptPasswordEncoder passwordEncoder;

    @Override
    public String create(String userId, String emailRaw, String emailNorm) {
        long nowSec  = Instant.now().getEpochSecond();
        long expires = nowSec + vprops.getTtlSeconds();

        String verificationId = UUID.randomUUID().toString();

        var entry = VerificationEntry.builder()
                .verificationId(verificationId)
                .userId(userId)
                .emailPlain(emailNorm)
                .emailHash(HashingUtil.sha256Hex(emailNorm))
                .status("PENDING")
                .expiresAt(expires)
                .createdAt(Instant.ofEpochSecond(nowSec).toString())
                .build();

        repo.put(entry);

        String token     = codec.encode(verificationId);
        String verifyUrl = vprops.getLinkBaseUrl() + "?token=" + token;
        String notMeUrl  = vprops.getLinkBaseUrl().replace("/verify", "/verify/not-me") + "?token=" + token;

        notifier.sendEmailVerification(emailRaw, verifyUrl, notMeUrl);
        log.info("verification.created userId={} verificationId={} ttlSec={}", userId, verificationId, vprops.getTtlSeconds());
        return verificationId;
    }

    @Override
    public void resend(String emailRaw) {
        final String emailNorm = emailRaw == null ? "" : emailRaw.trim().toLowerCase(Locale.ROOT);
        final String emailHash = HashingUtil.sha256Hex(emailNorm);

        // Find account by email hash (GSI)
        var q = ddb.query(QueryRequest.builder()
                .tableName(AccountAttrs.TABLE)
                .indexName(AccountAttrs.GSI_EMAIL)
                .keyConditionExpression("#e = :eh")
                .expressionAttributeNames(Map.of("#e", AccountAttrs.EMAIL_HASH))
                .expressionAttributeValues(Map.of(":eh", AttributeValue.builder().s(emailHash).build()))
                .limit(1)
                .build());

        if (!q.hasItems() || q.items().isEmpty()) {
            throw new VerificationNotFoundException("email");
        }
        String userId = q.items().get(0).get(AccountAttrs.PK_USERID).s();

        // Check if already verified
        var acc = ddb.getItem(GetItemRequest.builder()
                .tableName(AccountAttrs.TABLE)
                .key(Map.of(AccountAttrs.PK_USERID, AttributeValue.builder().s(userId).build()))
                .build());

        if (acc.hasItem()
                && acc.item().containsKey(AccountAttrs.IS_VERIFIED)
                && Boolean.TRUE.equals(acc.item().get(AccountAttrs.IS_VERIFIED).bool())) {
            // reuse existing exception type to signal "cannot resend"
            throw new VerificationAlreadyUsedException("already_verified");
        }

        // Issue a new verification (controller orchestrates; we do the work)
        create(userId, emailRaw, emailNorm);
    }

    @Override
    public AccountResponse verifyByToken(String token) {
        final String verificationId;
        try {
            verificationId = codec.decodeAndVerify(token);
        } catch (InvalidVerificationTokenException e) {
            throw e;
        } catch (Exception e) {
            throw new InvalidVerificationTokenException("Signature or format invalid");
        }

        // Load verification row (consistent read)
        var opt = repo.getById(verificationId, true);
        var v = opt.orElseThrow(() -> new VerificationNotFoundException("id=" + verificationId));

        final long nowSec = Instant.now().getEpochSecond();
        if (v.isExpired(nowSec)) throw new VerificationExpiredException("verificationId=" + verificationId);
        if (!v.isPending()) throw new VerificationAlreadyUsedException("status=" + v.getStatus());

        // Transaction: mark account verified + mark verification USED
        final String nowIso = Instant.now().toString();
        Map<String, AttributeValue> vkey = Map.of(
                VerificationAttrs.PK_VERIFICATION_ID, AttributeValue.builder().s(verificationId).build()
        );

        ddb.transactWriteItems(TransactWriteItemsRequest.builder()
                .transactItems(
                        TransactWriteItem.builder().update(Update.builder()
                                .tableName(AccountAttrs.TABLE)
                                .key(Map.of(AccountAttrs.PK_USERID, AttributeValue.builder().s(v.getUserId()).build()))
                                .updateExpression("SET #v = :true, #u = :now")
                                .expressionAttributeNames(Map.of("#v", AccountAttrs.IS_VERIFIED, "#u", AccountAttrs.UPDATED_AT))
                                .expressionAttributeValues(Map.of(
                                        ":true", AttributeValue.builder().bool(true).build(),
                                        ":now", AttributeValue.builder().s(nowIso).build()
                                ))
                                .build()).build(),
                        TransactWriteItem.builder().update(Update.builder()
                                .tableName(VerificationAttrs.TABLE)
                                .key(vkey)
                                .conditionExpression("#s = :pending AND #exp > :nowNum")
                                .updateExpression("SET #s = :used, #va = :now")
                                .expressionAttributeNames(Map.of(
                                        "#s", VerificationAttrs.STATUS,
                                        "#va", VerificationAttrs.VERIFIED_AT,
                                        "#exp", VerificationAttrs.EXPIRES_AT
                                ))
                                .expressionAttributeValues(Map.of(
                                        ":pending", AttributeValue.builder().s("PENDING").build(),
                                        ":used", AttributeValue.builder().s("USED").build(),
                                        ":now", AttributeValue.builder().s(nowIso).build(),
                                        ":nowNum", AttributeValue.builder().n(Long.toString(nowSec)).build()
                                ))
                                .build()).build()
                ).build());

        // Read account for createdAt (and optionally email, if you decide to store it there)
        var acc = ddb.getItem(GetItemRequest.builder()
                .tableName(AccountAttrs.TABLE)
                .key(Map.of(AccountAttrs.PK_USERID, AttributeValue.builder().s(v.getUserId()).build()))
                .build()).item();

        return AccountResponse.builder()
                .userid(v.getUserId())
                .email(Optional.ofNullable(v.getEmailPlain()).orElse(""))
                .isVerified(true)
                .createdAt(acc.get(AccountAttrs.CREATED_AT).s())
                .updatedAt(nowIso)
                .build();
    }

    @Override
    public ResolvedEmail resolveEmailForToken(String token) {
        String id = codec.decodeAndVerify(token);
        var v = repo.getById(id, true).orElseThrow(() -> new VerificationNotFoundException("id=" + id));
        return new ResolvedEmail(id, v.getUserId(), Optional.ofNullable(v.getEmailPlain()).orElseThrow(
                () -> new VerificationNotFoundException("email_missing")));
    }
}
