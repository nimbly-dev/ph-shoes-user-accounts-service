package com.nimbly.phshoesbackend.useraccount.web.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbly.phshoesbackend.useraccount.core.auth.exception.InvalidCredentialsException;
import com.nimbly.phshoesbackend.useraccount.core.service.AccountSettingsService;
import com.nimbly.phshoesbackend.useraccounts.api.UserAccountSettingsApi;
import com.nimbly.phshoesbackend.commons.core.security.jwt.JwtTokenService;
import com.nimbly.phshoesbackend.commons.core.security.jwt.JwtVerificationException;
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
    private final JwtTokenService jwtTokenService;
    private final NativeWebRequest nativeWebRequest;
    private final ObjectMapper objectMapper;

    public UserAccountSettingsController(
            AccountSettingsService accountSettingsService,
            JwtTokenService jwtTokenService,
            NativeWebRequest nativeWebRequest,
            ObjectMapper objectMapper
    ) {
        this.accountSettingsService = accountSettingsService;
        this.jwtTokenService = jwtTokenService;
        this.nativeWebRequest = nativeWebRequest;
        this.objectMapper = objectMapper;
    }

    @Override
    public ResponseEntity<Object> getAccountSettings() {
        String authorizationHeader = nativeWebRequest.getHeader(HttpHeaders.AUTHORIZATION);
        String userId = extractUserId(authorizationHeader);

        JsonNode node = accountSettingsService.getOrInit(userId);
        Object body = objectMapper.convertValue(node, Object.class);
        return ResponseEntity.ok(body);
    }

    @Override
    public ResponseEntity<Map<String, Object>> updateAccountSettings(@Valid Object body) {
        String authorizationHeader = nativeWebRequest.getHeader(HttpHeaders.AUTHORIZATION);
        String userId = extractUserId(authorizationHeader);

        JsonNode incoming = objectMapper.valueToTree(body);
        JsonNode updated = accountSettingsService.update(userId, incoming);

        Map<String, Object> response = objectMapper.convertValue(updated, Map.class);
        return ResponseEntity.ok(response);
    }

    private String extractUserId(String authorizationHeader) {
        try {
            return jwtTokenService.userIdFromAuthorizationHeader(authorizationHeader);
        } catch (JwtVerificationException ex) {
            throw new InvalidCredentialsException();
        }
    }
}
