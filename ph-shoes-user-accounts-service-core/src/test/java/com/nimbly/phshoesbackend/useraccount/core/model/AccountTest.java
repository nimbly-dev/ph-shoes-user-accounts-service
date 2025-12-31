package com.nimbly.phshoesbackend.useraccount.core.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AccountTest {

    @Test
    void gettersSetters_roundTrip() {
        // Arrange
        Account account = new Account();
        Instant now = Instant.now();
        Instant updatedAt = now.plusSeconds(10);
        Instant lockUntil = now.plusSeconds(60);
        Instant lastLoginAt = now.plusSeconds(30);

        // Act
        account.setUserId("user-1");
        account.setEmailHash("hash1");
        account.setEmailEnc("enc");
        account.setPasswordHash("pwd");
        account.setIsVerified(Boolean.TRUE);
        account.setCreatedAt(now);
        account.setUpdatedAt(updatedAt);
        account.setSettingsJson("{\"k\":\"v\"}");
        account.setLoginFailCount(2);
        account.setLockUntil(lockUntil);
        account.setLastLoginAt(lastLoginAt);
        account.setLastLoginIp("127.0.0.1");
        account.setLastLoginUserAgent("ua");

        // Assert
        assertEquals("user-1", account.getUserId());
        assertEquals("hash1", account.getEmailHash());
        assertEquals("enc", account.getEmailEnc());
        assertEquals("pwd", account.getPasswordHash());
        assertEquals(Boolean.TRUE, account.getIsVerified());
        assertEquals(now, account.getCreatedAt());
        assertNotNull(account.getUpdatedAt());
        assertEquals("{\"k\":\"v\"}", account.getSettingsJson());
        assertEquals(Integer.valueOf(2), account.getLoginFailCount());
        assertEquals(lockUntil, account.getLockUntil());
        assertEquals(lastLoginAt, account.getLastLoginAt());
        assertEquals("127.0.0.1", account.getLastLoginIp());
        assertEquals("ua", account.getLastLoginUserAgent());

        Account other = new Account();
        other.setUserId("user-1");
        other.setEmailHash("hash1");
        other.setEmailEnc("enc");
        other.setPasswordHash("pwd");
        other.setIsVerified(Boolean.TRUE);
        other.setCreatedAt(now);
        other.setUpdatedAt(updatedAt);
        other.setSettingsJson("{\"k\":\"v\"}");
        other.setLoginFailCount(2);
        other.setLockUntil(lockUntil);
        other.setLastLoginAt(lastLoginAt);
        other.setLastLoginIp("127.0.0.1");
        other.setLastLoginUserAgent("ua");

        assertEquals(account, other);
        assertEquals(account.hashCode(), other.hashCode());
        assertTrue(account.toString().contains("user-1"));
    }
}
