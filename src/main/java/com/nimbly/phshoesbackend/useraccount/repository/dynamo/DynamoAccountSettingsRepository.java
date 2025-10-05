package com.nimbly.phshoesbackend.useraccount.repository.dynamo;

import com.nimbly.phshoesbackend.useraccount.model.AccountAttrs;
import com.nimbly.phshoesbackend.useraccount.repository.AccountSettingsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemRequest;

import java.util.Map;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class DynamoAccountSettingsRepository implements AccountSettingsRepository {

    private final DynamoDbClient ddb;

    @Override
    public Optional<String> getSettingsJson(String userId) {
        var res = ddb.getItem(GetItemRequest.builder()
                .tableName(AccountAttrs.TABLE)
                .key(Map.of(AccountAttrs.PK_USERID, AttributeValue.builder().s(userId).build()))
                .projectionExpression(String.join(",", AccountAttrs.PK_USERID, AccountAttrs.SETTINGS_JSON))
                .consistentRead(true)
                .build());

        if (!res.hasItem()) return Optional.empty();
        var item = res.item();
        if (!item.containsKey(AccountAttrs.SETTINGS_JSON)) return Optional.ofNullable(null);
        return Optional.of(item.get(AccountAttrs.SETTINGS_JSON).s());
    }

    @Override
    public void putSettingsJson(String userId, String settingsJson) {
        ddb.updateItem(UpdateItemRequest.builder()
                .tableName(AccountAttrs.TABLE)
                .key(Map.of(AccountAttrs.PK_USERID, AttributeValue.builder().s(userId).build()))
                .updateExpression("SET #s = :json, #u = :now")
                .conditionExpression("attribute_exists(#pk)")
                .expressionAttributeNames(Map.of(
                        "#s", AccountAttrs.SETTINGS_JSON,
                        "#u", AccountAttrs.UPDATED_AT,
                        "#pk", AccountAttrs.PK_USERID
                ))
                .expressionAttributeValues(Map.of(
                        ":json", AttributeValue.builder().s(settingsJson).build(),
                        ":now", AttributeValue.builder().s(java.time.Instant.now().toString()).build()
                ))
                .build());
    }
}
