package com.nimbly.phshoesbackend.useraccount.core.model.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@NoArgsConstructor
public class SessionItemDto {
    private String  jti;
    private String  userId;
    private String  status;     // "ACTIVE" | "REVOKED"
    private Long    ttl;
    private Instant createdAt;
    private Instant expiresAt;
    private Instant revokedAt;
}
