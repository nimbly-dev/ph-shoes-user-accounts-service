package com.nimbly.phshoesbackend.useraccount.core.service.impl;

import com.nimbly.phshoesbackend.commons.core.model.SuppressionEntry;
import com.nimbly.phshoesbackend.commons.core.model.SuppressionReason;
import com.nimbly.phshoesbackend.commons.core.repository.SuppressionRepository;
import com.nimbly.phshoesbackend.commons.core.security.EmailCrypto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SuppressionServiceImplTest {

    @Mock
    private SuppressionRepository repository;
    @Mock
    private EmailCrypto emailCrypto;

    private SuppressionServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new SuppressionServiceImpl(repository, emailCrypto);
    }

    @Test
    void shouldBlock_returnsFalse_whenNormalizedNull() {
        // Arrange
        when(emailCrypto.normalize("user@example.com")).thenReturn(null);

        // Act
        boolean blocked = service.shouldBlock("user@example.com");

        // Assert
        assertFalse(blocked);
    }

    @Test
    void shouldBlock_returnsTrue_whenHashSuppressed() {
        // Arrange
        when(emailCrypto.normalize("user@example.com")).thenReturn("user@example.com");
        when(emailCrypto.hashCandidates("user@example.com")).thenReturn(List.of("hash1", "hash2"));
        when(repository.isSuppressed("hash1")).thenReturn(false);
        when(repository.isSuppressed("hash2")).thenReturn(true);

        // Act
        boolean blocked = service.shouldBlock("user@example.com");

        // Assert
        assertTrue(blocked);
    }

    @Test
    void suppressHash_savesEntry() {
        // Arrange
        String emailHash = "hash1";

        // Act
        service.suppressHash(emailHash, SuppressionReason.COMPLAINT, "source", "notes", 100L);

        // Assert
        ArgumentCaptor<SuppressionEntry> captor = ArgumentCaptor.forClass(SuppressionEntry.class);
        verify(repository).put(captor.capture());
        SuppressionEntry entry = captor.getValue();
        assertEquals(emailHash, entry.getEmailHash());
        assertEquals(SuppressionReason.COMPLAINT, entry.getReason());
        assertEquals("source", entry.getSource());
        assertEquals("notes", entry.getNotes());
    }

    @Test
    void unsuppress_removesAllHashes() {
        // Arrange
        when(emailCrypto.normalize("user@example.com")).thenReturn("user@example.com");
        when(emailCrypto.hashCandidates("user@example.com")).thenReturn(List.of("hash1", "hash2"));

        // Act
        service.unsuppress("user@example.com");

        // Assert
        verify(repository).remove("hash1");
        verify(repository).remove("hash2");
    }
}
