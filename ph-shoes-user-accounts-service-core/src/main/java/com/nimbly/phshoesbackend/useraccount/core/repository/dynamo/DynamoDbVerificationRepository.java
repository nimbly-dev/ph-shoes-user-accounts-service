package com.nimbly.phshoesbackend.useraccount.core.repository.dynamo;

import com.nimbly.phshoesbackend.useraccount.core.model.VerificationEntry;
import com.nimbly.phshoesbackend.useraccount.core.model.VerificationStatus;
import com.nimbly.phshoesbackend.useraccount.core.model.dynamo.VerificationAttrs;
import com.nimbly.phshoesbackend.useraccount.core.repository.VerificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.core.pagination.sync.SdkIterable;
import software.amazon.awssdk.enhanced.dynamodb.*;
import software.amazon.awssdk.enhanced.dynamodb.model.*;
import software.amazon.awssdk.enhanced.dynamodb.Expression;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.time.Instant;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class DynamoDbVerificationRepository implements VerificationRepository {

    private final DynamoDbEnhancedClient enhanced;

    private DynamoDbTable<VerificationEntry> table() {
        return enhanced.table(VerificationAttrs.TABLE, TableSchema.fromBean(VerificationEntry.class));
    }

    @Override
    public void put(VerificationEntry entry) {
        table().putItem(entry);
    }

    @Override
    public Optional<VerificationEntry> getById(String verificationId, boolean consistentRead) {
        var req = GetItemEnhancedRequest.builder()
                .key(Key.builder().partitionValue(verificationId).build())
                .consistentRead(consistentRead)
                .build();
        return Optional.ofNullable(table().getItem(req));
    }

    @Override
    public void markUsedIfPendingAndNotExpired(String verificationId, long nowEpochSeconds) {
        var partial = new VerificationEntry();
        partial.setVerificationId(verificationId);
        partial.setStatus(VerificationStatus.VERIFIED);
        partial.setVerifiedAt(Instant.ofEpochSecond(nowEpochSeconds)); // keep Instant type

        var cond = Expression.builder()
                .expression("#st = :pending AND #exp > :now")
                .putExpressionName("#st", VerificationAttrs.STATUS)
                .putExpressionName("#exp", VerificationAttrs.EXPIRES_AT)
                .putExpressionValue(":pending", AttributeValue.fromS(VerificationStatus.PENDING.name()))
                .putExpressionValue(":now", AttributeValue.fromN(Long.toString(nowEpochSeconds)))
                .build();

        table().updateItem(UpdateItemEnhancedRequest.builder(VerificationEntry.class)
                .item(partial)
                .ignoreNulls(true)
                .conditionExpression(cond)
                .build());
    }

    @Override
    public void markStatusIfPending(String verificationId, VerificationStatus newStatus) {
        var partial = new VerificationEntry();
        partial.setVerificationId(verificationId);
        partial.setStatus(newStatus);

        var cond = Expression.builder()
                .expression("#st = :pending")
                .putExpressionName("#st", VerificationAttrs.STATUS)
                .putExpressionValue(":pending", AttributeValue.fromS(VerificationStatus.PENDING.name()))
                .build();

        table().updateItem(UpdateItemEnhancedRequest.builder(VerificationEntry.class)
                .item(partial)
                .ignoreNulls(true)
                .conditionExpression(cond)
                .build());
    }

    @Override
    public boolean hasVerifiedEntryForEmailHash(String emailHash) {
        DynamoDbIndex<VerificationEntry> index = table().index(VerificationAttrs.GSI_EMAIL);
        SdkIterable<Page<VerificationEntry>> pages = index.query(r -> r
                .queryConditional(QueryConditional.keyEqualTo(
                        Key.builder().partitionValue(emailHash).build()))
        );
        for (Page<VerificationEntry> page : pages) {
            for (VerificationEntry entry : page.items()) {
                if (entry.getStatus() == VerificationStatus.VERIFIED) {
                    return true;
                }
            }
        }
        return false;
    }
}
