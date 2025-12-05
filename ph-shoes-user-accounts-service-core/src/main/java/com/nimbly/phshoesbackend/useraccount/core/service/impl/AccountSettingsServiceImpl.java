package com.nimbly.phshoesbackend.useraccount.core.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbly.phshoesbackend.useraccount.core.repository.AccountSettingsRepository;
import com.nimbly.phshoesbackend.useraccount.core.exception.AccountNotFoundException;
import com.nimbly.phshoesbackend.useraccount.core.service.AccountSettingsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;

import java.nio.charset.StandardCharsets;

@Slf4j
@Service
@RequiredArgsConstructor
public class AccountSettingsServiceImpl implements AccountSettingsService {

    private final AccountSettingsRepository repo;
    private final ObjectMapper mapper;

    private static final String DEFAULT_SETTINGS_JSON = """
      {
        "Notification_Email_Preferences": {
          "Email_Notifications": true
        }
      }
      """;

    @Override
    public JsonNode getOrInit(String userId) {
        var existing = repo.getSettingsJson(userId);
        if (existing.isPresent() && existing.get() != null && !existing.get().isBlank()) {
            return parse(existing.get());
        }

        try {
            repo.putSettingsJson(userId, DEFAULT_SETTINGS_JSON);
            return parse(DEFAULT_SETTINGS_JSON);
        } catch (ConditionalCheckFailedException ex) {
            throw new AccountNotFoundException("userId=" + userId);
        }
    }

    @Override
    public JsonNode update(String userId, JsonNode settings) {
        try {
            if (settings == null || settings.isNull()) {
                // allow clearing settings (REMOVE attribute)
                repo.putSettingsJson(userId, null);
                return null;
            }

            String json = mapper.writeValueAsString(settings);
            if (json.getBytes(StandardCharsets.UTF_8).length > 64 * 1024) {
                throw new IllegalArgumentException("settings too large (>64KB)");
            }
            repo.putSettingsJson(userId, json);
            return settings;
        } catch (ConditionalCheckFailedException e) {
            throw new AccountNotFoundException("userId=" + userId);
        } catch (Exception e) {
            throw new RuntimeException("Failed to update settings", e);
        }
    }

    private JsonNode parse(String json) {
        try { return mapper.readTree(json); }
        catch (Exception e) { throw new RuntimeException("Invalid settings JSON in DB", e); }
    }
}
