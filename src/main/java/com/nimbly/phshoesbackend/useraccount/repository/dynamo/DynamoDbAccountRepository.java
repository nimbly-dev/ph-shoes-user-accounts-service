package com.nimbly.phshoesbackend.useraccount.repository.dynamo;

import com.nimbly.phshoesbackend.useraccount.model.Account;
import com.nimbly.phshoesbackend.useraccount.model.dto.VerificationData;
import com.nimbly.phshoesbackend.useraccount.model.dto.VerificationItemDto;
import com.nimbly.phshoesbackend.useraccount.repository.AccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbIndex;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.mapper.StaticAttributeTags;
import software.amazon.awssdk.enhanced.dynamodb.mapper.StaticTableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryEnhancedRequest;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class DynamoDbAccountRepository implements AccountRepository {

    private static final String TABLE = "accounts";
    private static final String GSI_EMAIL = "gsi_email";

    private static final String VERIF_TABLE = "verifications";

    private final DynamoDbEnhancedClient enhanced;

    /** accounts table schema */
    private static final TableSchema<Account> ACCOUNT_SCHEMA =
            StaticTableSchema.builder(Account.class)
                    .newItemSupplier(Account::new)

                    // Keys
                    .addAttribute(String.class, a -> a.name("userid")
                            .getter(Account::getUserid)
                            .setter(Account::setUserid)
                            .tags(StaticAttributeTags.primaryPartitionKey()))
                    .addAttribute(String.class, a -> a.name("email")
                            .getter(Account::getEmail)
                            .setter(Account::setEmail)
                            .tags(StaticAttributeTags.secondaryPartitionKey(GSI_EMAIL)))

                    // Auth / flags
                    .addAttribute(String.class,  a -> a.name("password")
                            .getter(Account::getPassword)
                            .setter(Account::setPassword))
                    .addAttribute(Boolean.class, a -> a.name("isVerified")
                            .getter(Account::getEmailVerified)
                            .setter(Account::setEmailVerified))
                    .addAttribute(Integer.class, a -> a.name("loginFailCount")
                            .getter(Account::getLoginFailCount)
                            .setter(Account::setLoginFailCount))
                    .addAttribute(Long.class,    a -> a.name("lockUntil")
                            .getter(Account::getLockUntil)
                            .setter(Account::setLockUntil))

                    // Telemetry
                    .addAttribute(Instant.class, a -> a.name("lastLoginAt")
                            .getter(Account::getLastLoginAt)
                            .setter(Account::setLastLoginAt))
                    .addAttribute(String.class,  a -> a.name("lastLoginIp")
                            .getter(Account::getLastLoginIp)
                            .setter(Account::setLastLoginIp))
                    .addAttribute(String.class,  a -> a.name("lastLoginUserAgent")
                            .getter(Account::getLastLoginUserAgent)
                            .setter(Account::setLastLoginUserAgent))

                    // Housekeeping
                    .addAttribute(Instant.class, a -> a.name("createdAt")
                            .getter(Account::getCreatedAt)
                            .setter(Account::setCreatedAt))
                    .addAttribute(Instant.class, a -> a.name("updatedAt")
                            .getter(Account::getUpdatedAt)
                            .setter(Account::setUpdatedAt))
                    .build();

    private static final TableSchema<VerificationItemDto> VERIF_SCHEMA =
            StaticTableSchema.builder(VerificationItemDto.class)
                    .newItemSupplier(VerificationItemDto::new)
                    .addAttribute(String.class,  a -> a.name("id")
                            .getter(VerificationItemDto::getId).setter(VerificationItemDto::setId)
                            .tags(StaticAttributeTags.primaryPartitionKey()))
                    .addAttribute(String.class,  a -> a.name("userId")
                            .getter(VerificationItemDto::getUserId).setter(VerificationItemDto::setUserId))
                    .addAttribute(String.class,  a -> a.name("emailPlain")
                            .getter(VerificationItemDto::getEmailPlain).setter(VerificationItemDto::setEmailPlain))
                    .addAttribute(Instant.class, a -> a.name("createdAt")
                            .getter(VerificationItemDto::getCreatedAt).setter(VerificationItemDto::setCreatedAt))
                    .addAttribute(Instant.class, a -> a.name("expiresAt")
                            .getter(VerificationItemDto::getExpiresAt).setter(VerificationItemDto::setExpiresAt))
                    .addAttribute(Instant.class, a -> a.name("usedAt")
                            .getter(VerificationItemDto::getUsedAt).setter(VerificationItemDto::setUsedAt))
                    .addAttribute(Long.class,    a -> a.name("ttl")
                            .getter(VerificationItemDto::getTtl).setter(VerificationItemDto::setTtl))
                    .build();

    private DynamoDbTable<Account> table() {
        return enhanced.table(TABLE, ACCOUNT_SCHEMA);
    }

    private DynamoDbTable<VerificationItemDto> verifTable() {
        return enhanced.table(VERIF_TABLE, VERIF_SCHEMA);
    }

    @Override
    public Optional<Account> findById(String userId) {
        if (userId == null) return Optional.empty();
        return Optional.ofNullable(table().getItem(Key.builder().partitionValue(userId).build()));
    }

    @Override
    public Optional<Account> findByEmail(String email) {
        if (email == null) return Optional.empty();

        // 1) Query GSI with normalized+hashed email
        String key = emailHash(normalize(email));
        DynamoDbIndex<Account> idx = table().index(GSI_EMAIL);

        QueryEnhancedRequest req = QueryEnhancedRequest.builder()
                .queryConditional(QueryConditional.keyEqualTo(Key.builder().partitionValue(key).build()))
                .limit(1)
                .build();

        var firstFromIndex = idx.query(req)
                .stream()
                .flatMap(p -> p.items().stream())
                .findFirst();

        if (firstFromIndex.isEmpty()) return Optional.empty();

        // 2) Re-fetch full item by PK to ensure all attributes (e.g., password) are present
        String userId = firstFromIndex.get().getUserid();
        return findById(userId);
    }

    @Override
    public Account save(Account account) {
        return table().updateItem(account); // upsert
    }

    @Override
    public void recordFailedLogin(String email, int maxFailures, int lockSeconds) {
        findByEmail(email).ifPresent(acc -> {
            int fails = acc.getLoginFailCount() == null ? 0 : acc.getLoginFailCount();
            acc.setLoginFailCount(fails + 1);
            if (acc.getLoginFailCount() >= maxFailures) {
                acc.setLockUntil(Instant.now().plusSeconds(lockSeconds).toEpochMilli());
            }
            table().updateItem(acc);
        });
    }

    @Override
    public void recordSuccessfulLogin(String userId, String ip, String ua) {
        findById(userId).ifPresent(acc -> {
            acc.setLoginFailCount(0);
            acc.setLockUntil(null);
            acc.setLastLoginAt(Instant.now());
            acc.setLastLoginIp(ip);
            acc.setLastLoginUserAgent(ua);
            table().updateItem(acc);
        });
    }

    @Override
    public void deleteById(String userId) {
        if (userId == null || userId.isBlank()) return;
        table().deleteItem(Key.builder().partitionValue(userId).build());
    }


    @Override
    public String createVerification(String userId, String emailPlain, int ttlSeconds) {
        var id  = UUID.randomUUID().toString();
        var now = Instant.now();

        var v = new VerificationItemDto();
        v.setId(id);
        v.setUserId(userId);
        v.setEmailPlain(emailPlain);
        v.setCreatedAt(now);
        v.setExpiresAt(now.plusSeconds(ttlSeconds));
        v.setUsedAt(null);
        v.setTtl(now.plusSeconds(ttlSeconds + 60).getEpochSecond()); // TTL grace

        verifTable().putItem(v);
        return id;
    }

    @Override
    public Optional<VerificationData> findVerification(String id) {
        if (id == null || id.isBlank()) return Optional.empty();
        var v = verifTable().getItem(Key.builder().partitionValue(id).build());
        if (v == null) return Optional.empty();
        return Optional.of(new VerificationData(
                v.getId(), v.getUserId(), v.getEmailPlain(), v.getExpiresAt(), v.getUsedAt()
        ));
    }

    @Override
    public void markVerificationUsed(String id, Instant usedAt) {
        var key = Key.builder().partitionValue(id).build();
        var v = verifTable().getItem(key);
        if (v != null && v.getUsedAt() == null) {
            v.setUsedAt(usedAt);
            verifTable().updateItem(v);
        }
    }


    private static String normalize(String email) {
        return email.trim().toLowerCase();
    }

    private static String emailHash(String normalizedEmail) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] d = md.digest(normalizedEmail.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(d.length * 2);
            for (byte b : d) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to hash email", e);
        }
    }
}
