// DynamoAccountRepository.java
package com.nimbly.phshoesbackend.useraccount.core.repository.dynamo;

import com.nimbly.phshoesbackend.useraccount.core.model.Account;
import com.nimbly.phshoesbackend.useraccount.core.model.dynamo.AccountAttrs;
import com.nimbly.phshoesbackend.useraccount.core.repository.AccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class DynamoDbAccountRepository implements AccountRepository {

    private final DynamoDbClient ddb;

    @Override
    public Optional<Account> findByUserId(String userId) {
        var response = ddb.getItem(GetItemRequest.builder()
                .tableName(AccountAttrs.TABLE)
                .key(Map.of(AccountAttrs.PK_USERID, AttributeValue.fromS(userId)))
                .consistentRead(true)
                .build());

        if (!response.hasItem()) return Optional.empty();
        return Optional.of(mapToAccount(response.item()));
    }

    @Override
    public Optional<Account> findByEmailHash(String emailHash) {
        var query = ddb.query(QueryRequest.builder()
                .tableName(AccountAttrs.TABLE)
                .indexName(AccountAttrs.GSI_EMAIL)
                .keyConditionExpression("#k = :v")
                .expressionAttributeNames(Map.of("#k", AccountAttrs.EMAIL_HASH))
                .expressionAttributeValues(Map.of(":v", AttributeValue.fromS(emailHash)))
                .limit(1)
                .build());

        if (query.count() == 0) return Optional.empty();
        return Optional.of(mapToAccount(query.items().get(0)));
    }

    @Override
    public boolean existsByEmailHash(String emailHash) {
        var query = ddb.query(QueryRequest.builder()
                .tableName(AccountAttrs.TABLE)
                .indexName(AccountAttrs.GSI_EMAIL)
                .keyConditionExpression("#k = :v")
                .expressionAttributeNames(Map.of("#k", AccountAttrs.EMAIL_HASH))
                .expressionAttributeValues(Map.of(":v", AttributeValue.fromS(emailHash)))
                .limit(1)
                .build());
        return query.count() > 0;
    }

    @Override
    public void save(Account account) {
        var item = new LinkedHashMap<String, AttributeValue>(16);

        if (account.getUserId() != null)
            item.put(AccountAttrs.PK_USERID, AttributeValue.fromS(account.getUserId()));
        if (account.getEmailHash() != null)
            item.put(AccountAttrs.EMAIL_HASH, AttributeValue.fromS(account.getEmailHash()));
        if (account.getEmailEnc() != null)
            item.put(AccountAttrs.EMAIL_ENC, AttributeValue.fromS(account.getEmailEnc()));
        if (account.getPasswordHash() != null)
            item.put(AccountAttrs.PASSWORD_HASH, AttributeValue.fromS(account.getPasswordHash()));
        if (account.getIsVerified() != null)
            item.put(AccountAttrs.IS_VERIFIED, AttributeValue.fromBool(account.getIsVerified()));
        if (account.getCreatedAt() != null)
            item.put(AccountAttrs.CREATED_AT, AttributeValue.fromS(account.getCreatedAt().toString()));
        if (account.getUpdatedAt() != null)
            item.put(AccountAttrs.UPDATED_AT, AttributeValue.fromS(account.getUpdatedAt().toString()));
        if (account.getSettingsJson() != null)
            item.put(AccountAttrs.SETTINGS_JSON, AttributeValue.fromS(account.getSettingsJson()));
        if (account.getLoginFailCount() != null)
            item.put(AccountAttrs.LOGIN_FAIL_COUNT, AttributeValue.fromN(Integer.toString(account.getLoginFailCount())));
        if (account.getLockUntil() != null)
            item.put(AccountAttrs.LOCK_UNTIL, AttributeValue.fromS(account.getLockUntil().toString()));
        if (account.getLastLoginAt() != null)
            item.put(AccountAttrs.LAST_LOGIN_AT, AttributeValue.fromS(account.getLastLoginAt().toString()));
        if (account.getLastLoginIp() != null)
            item.put(AccountAttrs.LAST_LOGIN_IP, AttributeValue.fromS(account.getLastLoginIp()));
        if (account.getLastLoginUserAgent() != null)
            item.put(AccountAttrs.LAST_LOGIN_UA, AttributeValue.fromS(account.getLastLoginUserAgent()));

        ddb.putItem(PutItemRequest.builder()
                .tableName(AccountAttrs.TABLE)
                .item(item)
                .build());
    }

    @Override
    public void setVerified(String userId, boolean verified) {
        ddb.updateItem(UpdateItemRequest.builder()
                .tableName(AccountAttrs.TABLE)
                .key(Map.of(AccountAttrs.PK_USERID, AttributeValue.fromS(userId)))
                .updateExpression("SET #v = :v, #u = :now")
                .conditionExpression("attribute_exists(#pk)")
                .expressionAttributeNames(Map.of(
                        "#v", AccountAttrs.IS_VERIFIED,
                        "#u", AccountAttrs.UPDATED_AT,
                        "#pk", AccountAttrs.PK_USERID
                ))
                .expressionAttributeValues(Map.of(
                        ":v", AttributeValue.fromBool(verified),
                        ":now", AttributeValue.fromS(Instant.now().toString())
                ))
                .build());
    }


    private Account mapToAccount(Map<String, AttributeValue> item) {
        var account = new Account();

        var v = item.get(AccountAttrs.PK_USERID);
        account.setUserId(v == null ? null : v.s());

        v = item.get(AccountAttrs.EMAIL_HASH);
        account.setEmailHash(v == null ? null : v.s());

        v = item.get(AccountAttrs.EMAIL_ENC);
        account.setEmailEnc(v == null ? null : v.s());

        v = item.get(AccountAttrs.PASSWORD_HASH);
        account.setPasswordHash(v == null ? null : v.s());

        var b = item.get(AccountAttrs.IS_VERIFIED);
        account.setIsVerified(b == null ? null : b.bool());

        v = item.get(AccountAttrs.CREATED_AT);
        account.setCreatedAt(v == null || v.s() == null ? null : Instant.parse(v.s()));

        v = item.get(AccountAttrs.UPDATED_AT);
        account.setUpdatedAt(v == null || v.s() == null ? null : Instant.parse(v.s()));

        v = item.get(AccountAttrs.SETTINGS_JSON);
        account.setSettingsJson(v == null ? null : v.s());

        var n = item.get(AccountAttrs.LOGIN_FAIL_COUNT);
        account.setLoginFailCount(n == null || n.n() == null ? null : Integer.parseInt(n.n()));

        v = item.get(AccountAttrs.LOCK_UNTIL);
        account.setLockUntil(v == null || v.s() == null ? null : Instant.parse(v.s()));

        v = item.get(AccountAttrs.LAST_LOGIN_AT);
        account.setLastLoginAt(v == null || v.s() == null ? null : Instant.parse(v.s()));

        v = item.get(AccountAttrs.LAST_LOGIN_IP);
        account.setLastLoginIp(v == null ? null : v.s());

        v = item.get(AccountAttrs.LAST_LOGIN_UA);
        account.setLastLoginUserAgent(v == null ? null : v.s());

        return account;
    }

    @Override
    public void deleteByUserId(String userId) {
        ddb.deleteItem(DeleteItemRequest.builder()
                .tableName(AccountAttrs.TABLE)
                .key(Map.of(AccountAttrs.PK_USERID, AttributeValue.fromS(userId)))
                .build());
    }
}
