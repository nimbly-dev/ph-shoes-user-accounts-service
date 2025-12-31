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

import java.util.Optional;

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
        Optional<String> existing = repo.getSettingsJson(userId);
        String settingsJson = existing
                .filter(value -> value != null && !value.isBlank())
                .orElse(null);

        if (settingsJson == null) {
            try {
                repo.putSettingsJson(userId, DEFAULT_SETTINGS_JSON);
            } catch (ConditionalCheckFailedException ex) {
                throw new AccountNotFoundException("userId=" + userId);
            }
            settingsJson = DEFAULT_SETTINGS_JSON;
        }

        try {
            return mapper.readTree(settingsJson);
        } catch (Exception e) {
            throw new RuntimeException("Invalid settings JSON in DB", e);
        }
    }

    @Override
    public JsonNode update(String userId, JsonNode settings) {
        try {
            if (settings == null || settings.isNull()) {
                repo.putSettingsJson(userId, null);
                return null;
            }

            String json = mapper.writeValueAsString(settings);
            repo.putSettingsJson(userId, json);
            return settings;
        } catch (ConditionalCheckFailedException e) {
            log.error("account.setting: Failed to get account with userid {}", userId);
            throw new AccountNotFoundException("userId=" + userId);
        } catch (IllegalArgumentException e) {
            log.error("account.setting: Illegal Argument: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Error on account.setting.update : {}", e.getMessage());
            throw new RuntimeException("Failed to update settings", e);
        }
    }

}
