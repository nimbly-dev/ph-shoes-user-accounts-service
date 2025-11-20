// VerificationEntry.java
package com.nimbly.phshoesbackend.useraccount.core.model;

import com.nimbly.phshoesbackend.useraccount.core.model.dynamo.VerificationAttrs;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.*;

@Data
@NoArgsConstructor
@DynamoDbBean
public class VerificationEntry {

    @Getter(onMethod_ = {
            @DynamoDbPartitionKey,
            @DynamoDbAttribute(VerificationAttrs.PK_VERIFICATION_ID)
    })
    @Setter
    private String verificationId;

    @Getter(onMethod_ = {
            @DynamoDbAttribute(VerificationAttrs.USER_ID)
    })
    @Setter
    private String userId;

    // ensure a GSI exists if you use this as an index key
    @Getter(onMethod_ = {
            @DynamoDbSecondaryPartitionKey(indexNames = VerificationAttrs.GSI_EMAIL),
            @DynamoDbAttribute(VerificationAttrs.EMAIL_HASH)
    })
    @Setter
    private String emailHash;

    @Getter(onMethod_ = {
            @DynamoDbAttribute(VerificationAttrs.STATUS)
    })
    @Setter
    private VerificationStatus status;

    // TTL (epoch seconds)
    @Getter(onMethod_ = {
            @DynamoDbAttribute(VerificationAttrs.EXPIRES_AT)
    })
    @Setter
    private Long expiresAt;

    @Getter(onMethod_ = {
            @DynamoDbAttribute(VerificationAttrs.CREATED_AT)
    })
    @Setter
    private Instant createdAt;

    @Getter(onMethod_ = {
            @DynamoDbAttribute(VerificationAttrs.VERIFIED_AT)
    })
    @Setter
    private Instant verifiedAt;
}
