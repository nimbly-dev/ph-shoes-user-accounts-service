package com.nimbly.phshoesbackend.useraccount.repository.dynamo;

import com.nimbly.phshoesbackend.useraccount.config.DynamoConfigTables;
import com.nimbly.phshoesbackend.useraccount.model.SuppressionEntry;
import com.nimbly.phshoesbackend.useraccount.repository.SuppressionRepository;
import lombok.RequiredArgsConstructor;
import software.amazon.awssdk.enhanced.dynamodb.*;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class DynamoDbSuppressionRepository implements SuppressionRepository {
    private final DynamoDbClient dynamo;
    private final DynamoDbEnhancedClient enhancedClient;
    private final DynamoConfigTables tables;

    private DynamoDbTable<SuppressionEntry> table() {
        return enhancedClient.table(tables.suppressionsTableName(),
                TableSchema.fromBean(SuppressionEntry.class));
    }

    @Override
    public boolean isSuppressed(String email) {
        var item = table().getItem(r -> r.key(k -> k.partitionValue(email)));
        return item != null && (item.getExpiresAt() == null || item.getExpiresAt() > (System.currentTimeMillis() / 1000));
    }

    @Override
    public void put(SuppressionEntry entry) {
        table().putItem(entry);
    }

    @Override
    public void remove(String email) {
        table().deleteItem(r -> r.key(k -> k.partitionValue(email)));
    }

    @Override
    public SuppressionEntry get(String email) {
        return table().getItem(r -> r.key(k -> k.partitionValue(email)));
    }
}
