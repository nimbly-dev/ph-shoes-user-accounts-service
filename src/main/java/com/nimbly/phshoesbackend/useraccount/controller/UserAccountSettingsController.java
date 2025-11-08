package com.nimbly.phshoesbackend.useraccount.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbly.phshoesbackend.useraccount.auth.JwtTokenProvider;
import com.nimbly.phshoesbackend.useraccount.service.AccountSettingsService;
import com.nimbly.phshoesbackend.useraccounts.api.UserAccountSettingsApi;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.NativeWebRequest;

import java.util.Map;

@Slf4j
@RestController
public class UserAccountSettingsController implements UserAccountSettingsApi {

    private final AccountSettingsService accountSettingsService;
    private final JwtTokenProvider jwtTokenProvider;
    private final NativeWebRequest nativeWebRequest;
    private final ObjectMapper objectMapper;

    public UserAccountSettingsController(
            AccountSettingsService accountSettingsService,
            JwtTokenProvider jwtTokenProvider,
            NativeWebRequest nativeWebRequest,
            ObjectMapper objectMapper
    ) {
        this.accountSettingsService = accountSettingsService;
        this.jwtTokenProvider = jwtTokenProvider;
        this.nativeWebRequest = nativeWebRequest;
        this.objectMapper = objectMapper;
    }

    @Override
    public ResponseEntity<Object> getAccountSettings() {
        String authorizationHeader = nativeWebRequest.getHeader(HttpHeaders.AUTHORIZATION);
        String userId = jwtTokenProvider.userIdFromAuthorizationHeader(authorizationHeader);

        JsonNode node = accountSettingsService.getOrInit(userId);
        Object body = objectMapper.convertValue(node, Object.class);
        return ResponseEntity.ok(body);
    }

    @Override
    public ResponseEntity<Map<String, Object>> updateAccountSettings(@Valid Object body) {
        String authorizationHeader = nativeWebRequest.getHeader(HttpHeaders.AUTHORIZATION);
        String userId = jwtTokenProvider.userIdFromAuthorizationHeader(authorizationHeader);

        JsonNode incoming = objectMapper.valueToTree(body);
        JsonNode updated = accountSettingsService.update(userId, incoming);

        Map<String, Object> response = objectMapper.convertValue(updated, Map.class);
        return ResponseEntity.ok(response);
    }
}
