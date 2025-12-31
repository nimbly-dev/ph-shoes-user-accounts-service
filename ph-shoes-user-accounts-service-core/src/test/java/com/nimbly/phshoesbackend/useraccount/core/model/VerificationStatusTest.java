package com.nimbly.phshoesbackend.useraccount.core.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VerificationStatusTest {

    @Test
    void enumValues_roundTrip() {
        // Arrange
        VerificationStatus status = VerificationStatus.PENDING;

        // Act
        VerificationStatus parsed = VerificationStatus.valueOf("PENDING");

        // Assert
        assertEquals(status, parsed);
        assertEquals(5, VerificationStatus.values().length);
    }
}
