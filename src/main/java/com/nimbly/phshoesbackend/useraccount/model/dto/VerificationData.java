package com.nimbly.phshoesbackend.useraccount.model.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;


public record VerificationData(
        String id,
        String userId,
        String emailPlain,
        Instant expiresAt,
        Instant usedAt
) {}