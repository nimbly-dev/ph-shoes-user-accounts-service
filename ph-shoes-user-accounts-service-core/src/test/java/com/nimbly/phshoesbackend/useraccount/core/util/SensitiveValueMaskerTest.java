package com.nimbly.phshoesbackend.useraccount.core.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SensitiveValueMaskerTest {

    @Test
    void hashPrefix_returnsPrefix() {
        // Arrange
        String value = "abcdef123456";

        // Act
        String result = SensitiveValueMasker.hashPrefix(value);

        // Assert
        assertEquals("abcdef12", result);
    }

    @Test
    void maskEmail_masksLocalPart() {
        // Arrange
        String email = "user@example.com";

        // Act
        String result = SensitiveValueMasker.maskEmail(email);

        // Assert
        assertEquals("u***@example.com", result);
    }

    @Test
    void truncateForLog_shorterStringReturnsOriginal() {
        // Arrange
        String value = "short";

        // Act
        String result = SensitiveValueMasker.truncateForLog(value, 10);

        // Assert
        assertEquals("short", result);
    }
}
