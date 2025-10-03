package com.nimbly.phshoesbackend.useraccount.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;

import java.time.Instant;
import java.util.Map;

@Data
@DynamoDbBean
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SuppressionEntry {

    private String email;          // PK
    private SuppressionReason reason;         // BOUNCE | COMPLAINT | ABUSE | MANUAL | OTHER
    private String source;         // SES|SNS|SYSTEM|ADMIN|IMPORT
    private String notes;          // small free-text
    private Instant createdAt;     // ISO instant
    private Long expiresAt;        // epoch seconds (for TTL), nullable
    private Map<String, String> meta;

    @DynamoDbPartitionKey
    public String getEmail() { return email; }
}
