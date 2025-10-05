package com.nimbly.phshoesbackend.useraccount.model.dto;

import com.fasterxml.jackson.databind.JsonNode;
import software.amazon.awssdk.annotations.NotNull;

public record AccountSettingsUpdateRequest(@NotNull JsonNode settings) {
}
