package com.nimbly.phshoesbackend.useraccount.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.nimbly.phshoesbackend.useraccount.auth.JwtTokenProvider;
import com.nimbly.phshoesbackend.useraccount.model.dto.AccountSettingsResponse;
import com.nimbly.phshoesbackend.useraccount.model.dto.AccountSettingsUpdateRequest;
import com.nimbly.phshoesbackend.useraccount.service.AccountSettingsService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/user-accounts/setting")
public class UserAccountSettingsController {

    private final AccountSettingsService settingsService;
    private final JwtTokenProvider jwtTokenProvider;

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<AccountSettingsResponse> getSettings(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {

        String userId = jwtTokenProvider.userIdFromAuthorizationHeader(authorization);
        JsonNode settings = settingsService.getOrInit(userId);
        return ResponseEntity.ok(new AccountSettingsResponse(settings));
    }

    @PutMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<AccountSettingsResponse> updateSettings(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @Valid @RequestBody AccountSettingsUpdateRequest body) {

        String userId = jwtTokenProvider.userIdFromAuthorizationHeader(authorization);
        JsonNode updated = settingsService.update(userId, body.settings());
        return ResponseEntity.ok(new AccountSettingsResponse(updated));
    }
}
