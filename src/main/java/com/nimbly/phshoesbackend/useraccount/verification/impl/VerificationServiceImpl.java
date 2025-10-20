package com.nimbly.phshoesbackend.useraccount.verification.impl;

import com.nimbly.phshoesbackend.notification.core.model.dto.EmailAddress;
import com.nimbly.phshoesbackend.notification.core.model.dto.EmailRequest;
import com.nimbly.phshoesbackend.notification.core.model.dto.SendResult;
import com.nimbly.phshoesbackend.notification.core.service.NotificationService;
import com.nimbly.phshoesbackend.services.common.core.model.dynamo.AccountAttrs;
import com.nimbly.phshoesbackend.services.common.core.model.dynamo.VerificationAttrs;
import com.nimbly.phshoesbackend.services.common.core.repository.VerificationRepository;
import com.nimbly.phshoesbackend.useraccount.config.props.AppVerificationProps;
import com.nimbly.phshoesbackend.useraccount.exception.InvalidVerificationTokenException;
import com.nimbly.phshoesbackend.useraccount.exception.VerificationAlreadyUsedException;
import com.nimbly.phshoesbackend.useraccount.exception.VerificationExpiredException;
import com.nimbly.phshoesbackend.useraccount.exception.VerificationNotFoundException;
import com.nimbly.phshoesbackend.useraccount.model.ResolvedEmail;
import com.nimbly.phshoesbackend.useraccount.model.dto.AccountResponse;
import com.nimbly.phshoesbackend.useraccount.security.HashingUtil;
import com.nimbly.phshoesbackend.useraccount.verification.VerificationService;
import com.nimbly.phshoesbackend.useraccount.verification.VerificationTokenCodec;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItem;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItemsRequest;
import software.amazon.awssdk.services.dynamodb.model.Update;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class VerificationServiceImpl implements VerificationService {

    private final VerificationRepository verificationRepository;
    private final VerificationTokenCodec codec;
    private final NotificationService notificationService;
    private final AppVerificationProps vprops;
    private final DynamoDbClient ddb;
    private final BCryptPasswordEncoder passwordEncoder;


    private static String required(String name, String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing required property: " + name);
        }
        return value;
    }

    private String lookupUserIdByEmail(String emailRaw) {
        final String emailNorm = emailRaw == null ? "" : emailRaw.trim().toLowerCase(Locale.ROOT);
        final String emailHash = HashingUtil.sha256Hex(emailNorm);

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
        return q.items().get(0).get(AccountAttrs.PK_USERID).s();
    }

    private String createVerification(String userId, String emailPlain, long ttlSeconds) {
        final long nowSec = Instant.now().getEpochSecond();
        final long expiresAt = nowSec + ttlSeconds;
        final String verificationId = UUID.randomUUID().toString();

        Map<String, AttributeValue> item = Map.of(
                VerificationAttrs.PK_VERIFICATION_ID, AttributeValue.builder().s(verificationId).build(),
                VerificationAttrs.USER_ID, AttributeValue.builder().s(userId).build(),
                VerificationAttrs.EMAIL_PLAIN, AttributeValue.builder().s(emailPlain).build(),
                VerificationAttrs.STATUS, AttributeValue.builder().s("PENDING").build(),
                VerificationAttrs.CREATED_AT, AttributeValue.builder().s(Instant.ofEpochSecond(nowSec).toString()).build(),
                VerificationAttrs.EXPIRES_AT, AttributeValue.builder().n(Long.toString(expiresAt)).build()
        );

        ddb.putItem(PutItemRequest.builder()
                .tableName(VerificationAttrs.TABLE)
                .item(item)
                .build());

        return verificationId;
    }

    @Override
    public void sendVerificationEmail(String recipientEmail) {
        try {
            final String userId = lookupUserIdByEmail(recipientEmail);

            final long ttl = Optional.ofNullable(vprops.getTtlSeconds()).map(Long::valueOf).orElse(900L);
            final String verificationId = createVerification(userId, recipientEmail, ttl);
            final String token = codec.encode(verificationId);

            final String verifyBase = required("verification.verificationLink", vprops.getVerificationLink());
            final String notMeBase = required("verification.notMeLink", vprops.getNotMeLink());

            log.info("vprops linkbaseurl: {}", verifyBase);
            log.info("vprops notmebaseurl: {}", notMeBase);

            String verifyUrl = UriComponentsBuilder.fromHttpUrl(verifyBase)
                    .queryParam("token", token)
                    .build(true)
                    .toUriString();

            String notMeUrl = UriComponentsBuilder.fromHttpUrl(notMeBase)
                    .queryParam("token", token)
                    .build(true)
                    .toUriString();

            String template = new String(
                    new ClassPathResource("email/verification.html")
                            .getInputStream()
                            .readAllBytes(),
                    StandardCharsets.UTF_8
            );

            String html = template
                    .replace("{{VERIFY_URL}}", verifyUrl)
                    .replace("{{NOT_ME_URL}}", notMeUrl);

            EmailRequest email = EmailRequest.builder()
                    .from(EmailAddress.builder().address("no-reply@phshoes.local").build())
                    .to(EmailAddress.builder().address(recipientEmail).build())
                    .subject("Verify your PH Shoes Account")
                    .htmlBody(html)
                    .textBody("Verify your account: " + verifyUrl)
                    .build();

            SendResult result = notificationService.sendEmailVerification(email);
            log.info("Verification email sent to {} via {} messageId={}",
                    recipientEmail, result.getProvider(), result.getMessageId());

        } catch (Exception e) {
            log.error("Failed to send verification email to {}", recipientEmail, e);
        }
    }

    @Override
    public void resend(String emailRaw) {
        final String emailNorm = emailRaw == null ? "" : emailRaw.trim().toLowerCase(Locale.ROOT);
        final String emailHash = HashingUtil.sha256Hex(emailNorm);

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

        var acc = ddb.getItem(GetItemRequest.builder()
                .tableName(AccountAttrs.TABLE)
                .key(Map.of(AccountAttrs.PK_USERID, AttributeValue.builder().s(userId).build()))
                .build());

        if (acc.hasItem()
                && acc.item().containsKey(AccountAttrs.IS_VERIFIED)
                && Boolean.TRUE.equals(acc.item().get(AccountAttrs.IS_VERIFIED).bool())) {
            throw new VerificationAlreadyUsedException("already_verified");
        }

        // Issue a new verification by reusing the normal flow
        sendVerificationEmail(emailNorm);
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

        log.info("decoded token -> verificationId={}", verificationId);

        var get = ddb.getItem(GetItemRequest.builder()
                .tableName(VerificationAttrs.TABLE)
                .key(Map.of(
                        VerificationAttrs.PK_VERIFICATION_ID, AttributeValue.builder().s(verificationId).build()
                ))
                .consistentRead(true)
                .build());

        if (!get.hasItem() || get.item().isEmpty()) {
            throw new VerificationNotFoundException("id=" + verificationId);
        }
        var item = get.item();

        String userId      = item.getOrDefault(VerificationAttrs.USER_ID, AttributeValue.builder().s("").build()).s();
        String emailPlain  = Optional.ofNullable(item.get(VerificationAttrs.EMAIL_PLAIN)).map(AttributeValue::s).orElse("");
        String status      = Optional.ofNullable(item.get(VerificationAttrs.STATUS)).map(AttributeValue::s).orElse("PENDING");
        long   expiresAt   = Optional.ofNullable(item.get(VerificationAttrs.EXPIRES_AT)).map(AttributeValue::n).map(Long::parseLong).orElse(0L);

        final long nowSec = Instant.now().getEpochSecond();
        if (expiresAt <= nowSec) throw new VerificationExpiredException("verificationId=" + verificationId);
        if (!"PENDING".equals(status)) throw new VerificationAlreadyUsedException("status=" + status);

        final String nowIso = Instant.now().toString();
        Map<String, AttributeValue> vkey = Map.of(
                VerificationAttrs.PK_VERIFICATION_ID, AttributeValue.builder().s(verificationId).build()
        );

        ddb.transactWriteItems(TransactWriteItemsRequest.builder()
                .transactItems(
                        TransactWriteItem.builder().update(Update.builder()
                                .tableName(AccountAttrs.TABLE)
                                .key(Map.of(AccountAttrs.PK_USERID, AttributeValue.builder().s(userId).build()))
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

        // --- 3) Read account for createdAt (optional)
        var acc = ddb.getItem(GetItemRequest.builder()
                .tableName(AccountAttrs.TABLE)
                .key(Map.of(AccountAttrs.PK_USERID, AttributeValue.builder().s(userId).build()))
                .build()).item();

        return AccountResponse.builder()
                .userid(userId)
                .email(Optional.ofNullable(emailPlain).orElse(""))
                .isVerified(true)
                .createdAt(acc.get(AccountAttrs.CREATED_AT).s())
                .updatedAt(nowIso)
                .build();
    }


    @Override
    public ResolvedEmail resolveEmailForToken(String token) {
        final String id = codec.decodeAndVerify(token);

        var get = ddb.getItem(GetItemRequest.builder()
                .tableName(VerificationAttrs.TABLE)
                .key(Map.of(VerificationAttrs.PK_VERIFICATION_ID, AttributeValue.builder().s(id).build()))
                .consistentRead(true)
                .build());

        var item = get.item();
        if (item == null || item.isEmpty()) {
            throw new VerificationNotFoundException("id=" + id);
        }

        // SAFE reads
        String userId = Optional.ofNullable(item.get(VerificationAttrs.USER_ID))
                .map(AttributeValue::s).orElse(null);

        String emailPlain = Optional.ofNullable(item.get(VerificationAttrs.EMAIL_PLAIN))
                .map(AttributeValue::s).orElse(null);

        if (emailPlain == null || emailPlain.isBlank()) {
            throw new VerificationNotFoundException("email_missing");
        }
        if (userId == null || userId.isBlank()) {
            throw new VerificationNotFoundException("user_missing");
        }

        return new ResolvedEmail(id, userId, emailPlain);
    }
}
