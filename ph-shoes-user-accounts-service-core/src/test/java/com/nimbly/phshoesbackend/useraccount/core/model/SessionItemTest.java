package com.nimbly.phshoesbackend.useraccount.core.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SessionItemTest {

    @Test
    void gettersSetters_roundTrip() {
        // Arrange
        SessionItem sessionItem = new SessionItem();
        Instant now = Instant.now();

        // Act
        sessionItem.setSessionId("session-1");
        sessionItem.setUserId("user-1");
        sessionItem.setCreatedAt(now);
        sessionItem.setExpiresAt(1234L);
        sessionItem.setIp("127.0.0.1");
        sessionItem.setUserAgent("ua");
        sessionItem.setDataJson("{\"k\":\"v\"}");

        // Assert
        assertEquals("session-1", sessionItem.getSessionId());
        assertEquals("user-1", sessionItem.getUserId());
        assertEquals(now, sessionItem.getCreatedAt());
        assertEquals(Long.valueOf(1234L), sessionItem.getExpiresAt());
        assertEquals("127.0.0.1", sessionItem.getIp());
        assertEquals("ua", sessionItem.getUserAgent());
        assertNotNull(sessionItem.getDataJson());

        SessionItem other = new SessionItem();
        other.setSessionId("session-1");
        other.setUserId("user-1");
        other.setCreatedAt(now);
        other.setExpiresAt(1234L);
        other.setIp("127.0.0.1");
        other.setUserAgent("ua");
        other.setDataJson("{\"k\":\"v\"}");

        assertEquals(sessionItem, other);
        assertEquals(sessionItem.hashCode(), other.hashCode());
        assertTrue(sessionItem.toString().contains("session-1"));
    }
}
