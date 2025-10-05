package com.nimbly.phshoesbackend.useraccount.model.dto;

import com.fasterxml.jackson.databind.JsonNode;

public record AccountSettingsResponse(JsonNode settings) {
}
