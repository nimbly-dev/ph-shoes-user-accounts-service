package com.nimbly.phshoesbackend.useraccount.core.service;

import com.fasterxml.jackson.databind.JsonNode;

public interface AccountSettingsService {
    JsonNode getOrInit(String userId);
    JsonNode update(String userId, JsonNode settings);
}
