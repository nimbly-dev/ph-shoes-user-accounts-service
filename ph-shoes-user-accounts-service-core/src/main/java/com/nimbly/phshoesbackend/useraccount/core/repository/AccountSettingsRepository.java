package com.nimbly.phshoesbackend.useraccount.core.repository;

import java.util.Optional;

public interface AccountSettingsRepository {
    Optional<String> getSettingsJson(String userId);

    void putSettingsJson(String userId, String settingsJson);
}
