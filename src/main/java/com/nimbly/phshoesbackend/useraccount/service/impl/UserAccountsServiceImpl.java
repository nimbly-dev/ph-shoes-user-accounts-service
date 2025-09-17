package com.nimbly.phshoesbackend.useraccount.service.impl;

import com.nimbly.phshoesbackend.useraccount.config.AppVerificationProps;
import com.nimbly.phshoesbackend.useraccount.exception.EmailAlreadyRegisteredException;
import com.nimbly.phshoesbackend.useraccount.exception.InvalidVerificationTokenException;
import com.nimbly.phshoesbackend.useraccount.exception.VerificationAlreadyUsedException;
import com.nimbly.phshoesbackend.useraccount.exception.VerificationExpiredException;
import com.nimbly.phshoesbackend.useraccount.exception.VerificationNotFoundException;
import com.nimbly.phshoesbackend.useraccount.model.AccountAttrs;
import com.nimbly.phshoesbackend.useraccount.model.dto.AccountCreateRequest;
import com.nimbly.phshoesbackend.useraccount.model.dto.AccountResponse;
import com.nimbly.phshoesbackend.useraccount.repository.AccountRepository;
import com.nimbly.phshoesbackend.useraccount.service.NotificationService;
import com.nimbly.phshoesbackend.useraccount.service.UserAccountsService;
import com.nimbly.phshoesbackend.useraccount.security.HashingUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItem;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItemsRequest;
import software.amazon.awssdk.services.dynamodb.model.Update;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserAccountsServiceImpl implements UserAccountsService {

    private final DynamoDbClient ddb;
    private final BCryptPasswordEncoder passwordEncoder;
    private final NotificationService notifier;
    private final AppVerificationProps vprops;
    private final AccountRepository accountRepository;

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

        Map<String, AttributeValue> item = new HashMap<>();
        item.put(AccountAttrs.PK_USERID,      AttributeValue.builder().s(userId).build());
        item.put(AccountAttrs.EMAIL_HASH,     AttributeValue.builder().s(emailHash).build());
        item.put(AccountAttrs.PASSWORD_HASH,  AttributeValue.builder().s(passwordEncoder.encode(req.getPassword())).build());
        item.put(AccountAttrs.IS_VERIFIED,    AttributeValue.builder().bool(false).build());
        item.put(AccountAttrs.CREATED_AT,     AttributeValue.builder().s(now.toString()).build());
        item.put(AccountAttrs.UPDATED_AT,     AttributeValue.builder().s(now.toString()).build());

        ddb.putItem(PutItemRequest.builder()
                .tableName(AccountAttrs.TABLE)
                .item(item)
                .conditionExpression("attribute_not_exists(#pk)")
                .expressionAttributeNames(Map.of("#pk", AccountAttrs.PK_USERID))
                .build());

        // Best-effort: create verification + send email (resend endpoint covers failures)
        try {
            createAndSendVerification(userId, emailRaw, emailHash);
        } catch (Exception e) {
            log.warn("Failed to create/send verification for userid={}: {}", userId, e.toString());
        }

        return AccountResponse.builder()
                .userid(userId)
                .email(emailRaw) // plaintext not stored; returning the input is fine
                .isVerified(false)
                .createdAt(now.toString())
                .updatedAt(now.toString())
                .build();
    }

    @Override
    public void resendVerification(String emailRaw) {
        final String emailNorm = emailRaw == null ? "" : emailRaw.trim().toLowerCase(Locale.ROOT);
        final String emailHash = HashingUtil.sha256Hex(emailNorm);
        final String userId = findUserIdByEmailHash(emailHash);

        if (userId == null) {
            log.info("Resend requested for non-existing emailHash={}", emailHash.substring(0, Math.min(8, emailHash.length())));
            return;
        }

        if (Boolean.TRUE.equals(getAccountVerified(userId))) {
            log.info("Resend requested but account already verified userId={}", userId);
            return;
        }
        createAndSendVerification(userId, emailRaw, emailHash);
    }

    @Override
    public AccountResponse verifyByToken(String token) {
        final String verificationId;
        try {
            verificationId = parseAndVerifyToken(token);
        } catch (Exception e) {
            throw new InvalidVerificationTokenException("Signature or format invalid");
        }

        // 1) Load verification item
        Map<String, AttributeValue> vkey = Map.of(
                VerificationAttrs.PK_VERIFICATION_ID, AttributeValue.builder().s(verificationId).build()
        );
        var getV = ddb.getItem(GetItemRequest.builder()
                .tableName(VerificationAttrs.TABLE)
                .key(vkey)
                .consistentRead(true)
                .build());
        if (!getV.hasItem()) throw new VerificationNotFoundException("id=" + verificationId);
        var vitem = getV.item();

        String status    = vitem.get(VerificationAttrs.STATUS).s();
        long   expiresAt = Long.parseLong(vitem.get(VerificationAttrs.EXPIRES_AT).n());
        String userId    = vitem.get(VerificationAttrs.USER_ID).s();
        long   nowSec    = Instant.now().getEpochSecond();

        if (expiresAt <= nowSec) {
            throw new VerificationExpiredException("verificationId=" + verificationId);
        }
        if (!"PENDING".equals(status)) {
            throw new VerificationAlreadyUsedException("status=" + status);
        }

        // 2) Transaction: mark account verified + consume the verification (with condition on status & expiry)
        var nowIso = Instant.now().toString();
        ddb.transactWriteItems(TransactWriteItemsRequest.builder()
                .transactItems(
                        // Update account
                        TransactWriteItem.builder().update(Update.builder()
                                .tableName(AccountAttrs.TABLE)
                                .key(Map.of(AccountAttrs.PK_USERID, AttributeValue.builder().s(userId).build()))
                                .updateExpression("SET #v = :true, #u = :now")
                                .expressionAttributeNames(Map.of("#v", AccountAttrs.IS_VERIFIED, "#u", AccountAttrs.UPDATED_AT))
                                .expressionAttributeValues(Map.of(
                                        ":true", AttributeValue.builder().bool(true).build(),
                                        ":now",  AttributeValue.builder().s(nowIso).build()
                                ))
                                .build()).build(),
                        // Consume verification (ensure still pending & not expired)
                        TransactWriteItem.builder().update(Update.builder()
                                .tableName(VerificationAttrs.TABLE)
                                .key(vkey)
                                .conditionExpression("#s = :pending AND #exp > :nowNum")
                                .updateExpression("SET #s = :used, #va = :now")
                                .expressionAttributeNames(Map.of(
                                        "#s",   VerificationAttrs.STATUS,
                                        "#va",  VerificationAttrs.VERIFIED_AT,
                                        "#exp", VerificationAttrs.EXPIRES_AT
                                ))
                                .expressionAttributeValues(Map.of(
                                        ":pending", AttributeValue.builder().s("PENDING").build(),
                                        ":used",    AttributeValue.builder().s("USED").build(),
                                        ":now",     AttributeValue.builder().s(nowIso).build(),
                                        ":nowNum",  AttributeValue.builder().n(Long.toString(nowSec)).build()
                                ))
                                .build()).build()
                ).build());

        // 3) Return fresh account state
        var acc = ddb.getItem(GetItemRequest.builder()
                .tableName(AccountAttrs.TABLE)
                .key(Map.of(AccountAttrs.PK_USERID, AttributeValue.builder().s(userId).build()))
                .build()).item();

        return AccountResponse.builder()
                .userid(userId)
                .email(null) // plaintext email not stored
                .isVerified(true)
                .createdAt(acc.get(AccountAttrs.CREATED_AT).s())
                .updatedAt(nowIso)
                .build();
    }

    @Override
    public void deleteOwnAccount(String userId) {
        log.info("accounts.delete start userId={}", userId);
        accountRepository.deleteById(userId); // idempotent delete
        log.info("accounts.delete success userId={}", userId);
    }

    // -------------------- Helpers --------------------

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

    private String findUserIdByEmailHash(String emailHash) {
        var r = ddb.query(QueryRequest.builder()
                .tableName(AccountAttrs.TABLE)
                .indexName(AccountAttrs.GSI_EMAIL)
                .keyConditionExpression("#e = :eh")
                .expressionAttributeNames(Map.of("#e", AccountAttrs.EMAIL_HASH))
                .expressionAttributeValues(Map.of(":eh", AttributeValue.builder().s(emailHash).build()))
                .limit(1)
                .build());
        return (r.hasItems() && !r.items().isEmpty())
                ? r.items().get(0).get(AccountAttrs.PK_USERID).s()
                : null;
    }

    private Boolean getAccountVerified(String userId) {
        var acc = ddb.getItem(GetItemRequest.builder()
                .tableName(AccountAttrs.TABLE)
                .key(Map.of(AccountAttrs.PK_USERID, AttributeValue.builder().s(userId).build()))
                .build());
        if (!acc.hasItem()) return null;
        var item = acc.item();
        return item.containsKey(AccountAttrs.IS_VERIFIED) ? item.get(AccountAttrs.IS_VERIFIED).bool() : null;
    }

    private void createAndSendVerification(String userId, String emailRaw, String emailHash) {
        String verificationId = UUID.randomUUID().toString();
        long now = Instant.now().getEpochSecond();
        long expires = now + vprops.getTtlSeconds();

        var rng  = new SecureRandom();
        String code = String.format("%06d", rng.nextInt(1_000_000));
        String codeHash = passwordEncoder.encode(code);

        Map<String, AttributeValue> vitem = new HashMap<>();
        vitem.put(VerificationAttrs.PK_VERIFICATION_ID, AttributeValue.builder().s(verificationId).build());
        vitem.put(VerificationAttrs.USER_ID,    AttributeValue.builder().s(userId).build());
        vitem.put(VerificationAttrs.EMAIL_HASH, AttributeValue.builder().s(emailHash).build());
        vitem.put(VerificationAttrs.CODE_HASH,  AttributeValue.builder().s(codeHash).build()); // keep "code" attr for compatibility
        vitem.put(VerificationAttrs.STATUS,     AttributeValue.builder().s("PENDING").build());
        vitem.put(VerificationAttrs.EXPIRES_AT, AttributeValue.builder().n(Long.toString(expires)).build()); // TTL attr
        vitem.put(VerificationAttrs.CREATED_AT, AttributeValue.builder().s(Instant.ofEpochSecond(now).toString()).build());

        ddb.putItem(PutItemRequest.builder()
                .tableName(VerificationAttrs.TABLE)
                .item(vitem)
                .build());

        String token = buildToken(verificationId);
        String link  = vprops.getLinkBaseUrl() + "?token=" + token;

        notifier.sendEmailVerification(emailRaw, link, code);
        log.info("Verification created for userId={} verificationId={} (expires {}s)", userId, verificationId, vprops.getTtlSeconds());
    }

    private String buildToken(String verificationId) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(vprops.getSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] sig = mac.doFinal(verificationId.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(verificationId.getBytes(StandardCharsets.UTF_8))
                    + "." +
                    Base64.getUrlEncoder().withoutPadding().encodeToString(sig);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private String parseAndVerifyToken(String token) {
        String[] parts = token.split("\\.");
        if (parts.length != 2) throw new InvalidVerificationTokenException("Invalid token format");
        String id  = new String(Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8);
        byte[] sig = Base64.getUrlDecoder().decode(parts[1]);
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(vprops.getSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] expected = mac.doFinal(id.getBytes(StandardCharsets.UTF_8));
            if (!Arrays.equals(sig, expected)) throw new InvalidVerificationTokenException("Invalid token signature");
            return id;
        } catch (InvalidVerificationTokenException e) {
            throw e;
        } catch (Exception e) {
            throw new InvalidVerificationTokenException("Token verification failure");
        }
    }

    private static final class VerificationAttrs {
        static final String TABLE = "account_verifications";
        static final String PK_VERIFICATION_ID = "verificationId";
        static final String USER_ID     = "userId";
        static final String EMAIL_HASH  = "emailHash";
        static final String CODE_HASH   = "code";      // keep as-is to match your current table
        static final String STATUS      = "status";
        static final String EXPIRES_AT  = "expiresAt"; // TTL attribute (Number)
        static final String CREATED_AT  = "createdAt";
        static final String VERIFIED_AT = "verifiedAt";
    }
}
