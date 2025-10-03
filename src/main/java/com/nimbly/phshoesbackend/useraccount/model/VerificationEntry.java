package com.nimbly.phshoesbackend.useraccount.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;

import java.time.Instant;

@DynamoDbBean
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Data
public class VerificationEntry {
    // ---- Keys & identity ----
    private String verificationId; // PK
    private String userId;

    // ---- Email context ----
    private String emailHash;      // sha256(lowercased email)
    private String emailPlain;     // normalized/plain (lowercased)

    // ---- Token/flow status ----
    private String status;         // "PENDING" | "USED" | "CANCELLED"
    private String codeHash;       // optional: if you also support 6-digit code entry

    // ---- Timestamps ----
    private Long   expiresAt;      // epoch seconds (DynamoDB TTL attribute)
    private String createdAt;      // ISO-8601
    private String verifiedAt;     // ISO-8601 (nullable)

    // Partition key
    @DynamoDbPartitionKey
    public String getVerificationId() { return verificationId; }

    // Convenience helpers
    public boolean isExpired(long nowEpochSeconds) {
        return expiresAt != null && expiresAt <= nowEpochSeconds;
    }

    public boolean isPending() {
        return "PENDING".equals(status);
    }

    // Factory for a fresh PENDING entry
    public static VerificationEntry pending(
            String verificationId,
            String userId,
            String emailNorm,
            String emailHash,
            long expiresAtEpochSeconds,
            Instant createdAtInstant,
            String codeHash
    ) {
        return VerificationEntry.builder()
                .verificationId(verificationId)
                .userId(userId)
                .emailPlain(emailNorm)
                .emailHash(emailHash)
                .status("PENDING")
                .codeHash(codeHash)
                .expiresAt(expiresAtEpochSeconds)
                .createdAt(createdAtInstant.toString())
                .build();
    }
}
