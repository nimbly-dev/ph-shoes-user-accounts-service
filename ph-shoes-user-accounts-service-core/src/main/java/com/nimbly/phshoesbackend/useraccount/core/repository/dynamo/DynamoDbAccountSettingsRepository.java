package com.nimbly.phshoesbackend.useraccount.core.repository.dynamo;


import com.nimbly.phshoesbackend.useraccount.core.model.dynamo.AccountAttrs;
import com.nimbly.phshoesbackend.useraccount.core.repository.AccountSettingsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class DynamoDbAccountSettingsRepository implements AccountSettingsRepository {

    private final DynamoDbClient ddb;

    @Override
    public Optional<String> getSettingsJson(String userId) {
        var response = ddb.getItem(GetItemRequest.builder()
                .tableName(AccountAttrs.TABLE)
                .key(Map.of(AccountAttrs.PK_USERID, AttributeValue.fromS(userId)))
                .projectionExpression(String.join(",", AccountAttrs.PK_USERID, AccountAttrs.SETTINGS_JSON))
                .consistentRead(true)
                .build());

        if (!response.hasItem()) return Optional.empty();

        var item = response.item();
        var settings = item.get(AccountAttrs.SETTINGS_JSON);
        return settings == null || settings.s() == null ? Optional.empty() : Optional.of(settings.s());
    }

    @Override
    public void putSettingsJson(String userId, String settingsJson) {
        // If null → REMOVE the attribute to avoid storing "null" strings.
        if (settingsJson == null) {
            ddb.updateItem(UpdateItemRequest.builder()
                    .tableName(AccountAttrs.TABLE)
                    .key(Map.of(AccountAttrs.PK_USERID, AttributeValue.fromS(userId)))
                    .updateExpression("REMOVE #s SET #u = :now")
                    .conditionExpression("attribute_exists(#pk)")
                    .expressionAttributeNames(Map.of(
                            "#s", AccountAttrs.SETTINGS_JSON,
                            "#u", AccountAttrs.UPDATED_AT,
                            "#pk", AccountAttrs.PK_USERID
                    ))
                    .expressionAttributeValues(Map.of(
                            ":now", AttributeValue.fromS(Instant.now().toString())
                    ))
                    .build());
            return;
        }

        ddb.updateItem(UpdateItemRequest.builder()
                .tableName(AccountAttrs.TABLE)
                .key(Map.of(AccountAttrs.PK_USERID, AttributeValue.fromS(userId)))
                .updateExpression("SET #s = :json, #u = :now")
                .conditionExpression("attribute_exists(#pk)")
                .expressionAttributeNames(Map.of(
                        "#s", AccountAttrs.SETTINGS_JSON,
                        "#u", AccountAttrs.UPDATED_AT,
                        "#pk", AccountAttrs.PK_USERID
                ))
                .expressionAttributeValues(Map.of(
                        ":json", AttributeValue.fromS(settingsJson),
                        ":now", AttributeValue.fromS(Instant.now().toString())
                ))
                .build());
    }
}