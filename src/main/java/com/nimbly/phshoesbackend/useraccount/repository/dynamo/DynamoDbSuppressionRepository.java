package com.nimbly.phshoesbackend.useraccount.repository.dynamo;

import com.nimbly.phshoesbackend.useraccount.config.DynamoConfigTables;
import com.nimbly.phshoesbackend.useraccount.model.SuppressionAttrs;
import com.nimbly.phshoesbackend.useraccount.model.SuppressionEntry;
import com.nimbly.phshoesbackend.useraccount.model.SuppressionReason;
import com.nimbly.phshoesbackend.useraccount.repository.SuppressionRepository;
import lombok.RequiredArgsConstructor;
import software.amazon.awssdk.enhanced.dynamodb.*;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class DynamoDbSuppressionRepository implements SuppressionRepository {

    private final DynamoDbClient dynamo;

    private static Map<String, AttributeValue> key(String email) {
        return Map.of(SuppressionAttrs.PK_EMAIL, AttributeValue.builder().s(email).build());
    }

    @Override
    public boolean isSuppressed(String email) {
        var out = dynamo.getItem(GetItemRequest.builder()
                .tableName(SuppressionAttrs.TABLE)
                .key(key(email))
                .consistentRead(true)
                .build());

        var item = out.item();
        if (item == null || item.isEmpty()) return false;

        // No TTL means permanent suppression
        var ttl = item.get(SuppressionAttrs.EXPIRES_AT);
        if (ttl == null || ttl.n() == null || ttl.n().isBlank()) return true;

        long now = Instant.now().getEpochSecond();
        return Long.parseLong(ttl.n()) > now;
    }

    @Override
    public void put(SuppressionEntry entry) {
        Map<String, AttributeValue> item = new HashMap<>();

        item.put(SuppressionAttrs.PK_EMAIL,
                AttributeValue.builder().s(entry.getEmail()).build());

        Long expiresAt = entry.getExpiresAt();
        if (expiresAt != null) {
            item.put(SuppressionAttrs.EXPIRES_AT,
                    AttributeValue.builder().n(Long.toString(expiresAt)).build());
        }

        Instant createdAt = entry.getCreatedAt();
        if (createdAt != null) {
            item.put(SuppressionAttrs.CREATED_AT,
                    AttributeValue.builder().s(createdAt.toString()).build());
        }

        SuppressionReason reason = entry.getReason();
        if (reason != null) {
            item.put(SuppressionAttrs.REASON,
                    AttributeValue.builder().s(reason.name()).build());
        }

        String source = entry.getSource();
        if (source != null && !source.isBlank()) {
            item.put(SuppressionAttrs.SOURCE,
                    AttributeValue.builder().s(source).build());
        }

        PutItemRequest request = PutItemRequest.builder()
                .tableName(SuppressionAttrs.TABLE)
                .item(item)
                .build();

        dynamo.putItem(request);
    }


    @Override
    public void remove(String email) {
        dynamo.deleteItem(DeleteItemRequest.builder()
                .tableName(SuppressionAttrs.TABLE)
                .key(key(email))
                .build());
    }

    @Override
    public SuppressionEntry get(String email) {
        GetItemResponse out = dynamo.getItem(GetItemRequest.builder()
                .tableName(SuppressionAttrs.TABLE)
                .key(key(email))
                .consistentRead(true)
                .build());

        Map<String, AttributeValue> item = out.item();
        if (item == null || item.isEmpty()) {
            return null;
        }

        SuppressionEntry entry = new SuppressionEntry();
        entry.setEmail(email);

        AttributeValue ttl = item.get(SuppressionAttrs.EXPIRES_AT);
        if (ttl != null && ttl.n() != null && !ttl.n().isBlank()) {
            entry.setExpiresAt(Long.parseLong(ttl.n()));
        }

        AttributeValue created = item.get(SuppressionAttrs.CREATED_AT);
        if (created != null && created.s() != null && !created.s().isBlank()) {
            try {
                entry.setCreatedAt(Instant.parse(created.s()));
            } catch (java.time.format.DateTimeParseException ignore) {
                // leave null if bad format
            }
        }

        AttributeValue reason = item.get(SuppressionAttrs.REASON);
        if (reason != null && reason.s() != null && !reason.s().isBlank()) {
            try {
                entry.setReason(SuppressionReason.valueOf(reason.s()));
            } catch (IllegalArgumentException ignore) {
                // leave null if unknown enum constant
            }
        }

        AttributeValue source = item.get(SuppressionAttrs.SOURCE);
        if (source != null && source.s() != null && !source.s().isBlank()) {
            entry.setSource(source.s());
        }

        return entry;
    }
}
