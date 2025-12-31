package com.nimbly.phshoesbackend.useraccount.core.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VerificationEntryTest {

    @Test
    void gettersSetters_roundTrip() {
        // Arrange
        VerificationEntry entry = new VerificationEntry();
        Instant now = Instant.now();

        // Act
        entry.setVerificationId("verify-1");
        entry.setUserId("user-1");
        entry.setEmailHash("hash1");
        entry.setStatus(VerificationStatus.PENDING);
        entry.setExpiresAt(100L);
        entry.setCreatedAt(now);
        entry.setVerifiedAt(now.plusSeconds(5));

        // Assert
        assertEquals("verify-1", entry.getVerificationId());
        assertEquals("user-1", entry.getUserId());
        assertEquals("hash1", entry.getEmailHash());
        assertEquals(VerificationStatus.PENDING, entry.getStatus());
        assertEquals(Long.valueOf(100L), entry.getExpiresAt());
        assertEquals(now, entry.getCreatedAt());
        assertNotNull(entry.getVerifiedAt());

        VerificationEntry other = new VerificationEntry();
        other.setVerificationId("verify-1");
        other.setUserId("user-1");
        other.setEmailHash("hash1");
        other.setStatus(VerificationStatus.PENDING);
        other.setExpiresAt(100L);
        other.setCreatedAt(now);
        other.setVerifiedAt(now.plusSeconds(5));

        assertEquals(entry, other);
        assertEquals(entry.hashCode(), other.hashCode());
        assertTrue(entry.toString().contains("verify-1"));
    }
}
