package com.nimbly.phshoesbackend.useraccount.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class Account {
    // Keys
    private String userid;

    // Email
    private String email;

    // Auth
    private String password;
    private Boolean isVerified;

    // Audit
    private Instant createdAt;
    private Instant updatedAt;
}