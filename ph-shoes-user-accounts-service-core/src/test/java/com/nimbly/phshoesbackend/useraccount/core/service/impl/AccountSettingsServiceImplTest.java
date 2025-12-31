package com.nimbly.phshoesbackend.useraccount.core.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nimbly.phshoesbackend.useraccount.core.exception.AccountNotFoundException;
import com.nimbly.phshoesbackend.useraccount.core.repository.AccountSettingsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountSettingsServiceImplTest {

    @Mock
    private AccountSettingsRepository repository;

    private ObjectMapper objectMapper;
    private AccountSettingsServiceImpl service;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        service = new AccountSettingsServiceImpl(repository, objectMapper);
    }

    @Test
    void getOrInit_returnsExistingJson() {
        // Arrange
        String settingsJson = "{\"Notification_Email_Preferences\":{\"Email_Notifications\":false}}";
        when(repository.getSettingsJson("user-1")).thenReturn(Optional.of(settingsJson));

        // Act
        JsonNode response = service.getOrInit("user-1");

        // Assert
        assertNotNull(response);
        assertEquals(false, response.at("/Notification_Email_Preferences/Email_Notifications").asBoolean());
    }

    @Test
    void getOrInit_createsDefaultWhenMissing() {
        // Arrange
        when(repository.getSettingsJson("user-1")).thenReturn(Optional.empty());

        // Act
        JsonNode response = service.getOrInit("user-1");

        // Assert
        assertNotNull(response);
        assertEquals(true, response.at("/Notification_Email_Preferences/Email_Notifications").asBoolean());
        verify(repository).putSettingsJson(eq("user-1"), contains("Notification_Email_Preferences"));
    }

    @Test
    void update_clearsSettingsWhenNull() {
        // Arrange
        JsonNode settings = null;

        // Act
        JsonNode response = service.update("user-1", settings);

        // Assert
        assertNull(response);
        verify(repository).putSettingsJson("user-1", null);
    }

    @Test
    void update_throwsWhenUserMissing() {
        // Arrange
        ObjectNode node = objectMapper.createObjectNode();
        node.put("key", "value");
        doThrow(ConditionalCheckFailedException.builder().message("missing").build())
                .when(repository).putSettingsJson(eq("user-1"), anyString());

        // Act
        AccountNotFoundException exception = assertThrows(AccountNotFoundException.class, () -> service.update("user-1", node));

        // Assert
        assertNotNull(exception);
    }
}
