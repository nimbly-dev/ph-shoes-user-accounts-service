package com.nimbly.phshoesbackend.useraccount.web.controller;

import com.nimbly.phshoesbackend.useraccount.core.service.SuppressionService;
import com.nimbly.phshoesbackend.useraccount.core.subscribe.SubscribeService;
import com.nimbly.phshoesbackend.useraccount.core.unsubscribe.UnsubscribeService;
import com.nimbly.phshoesbackend.useraccount.core.util.SensitiveValueMasker;
import com.nimbly.phshoesbackend.useraccount.web.util.RedirectResponses;
import com.nimbly.phshoesbackend.useraccounts.api.UserAccountsEmailNotificationApi;
import com.nimbly.phshoesbackend.useraccounts.model.SubscriptionStatusResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Locale;

@Slf4j
@RestController
@RequiredArgsConstructor
public class UserAccountsEmailNotificationController implements UserAccountsEmailNotificationApi {

    private final UnsubscribeService unsubscribeService;
    private final SubscribeService subscribeService;
    private final SuppressionService suppressionService;

    @Value("${app.frontend.base-url:}")
    private String frontendBaseUrl;

    @Value("${app.frontend.unsubscribe-path:/}")
    private String frontendUnsubscribePath;

    @Override
    public ResponseEntity<Void> unsubscribeOneClick(
            @RequestParam(value = "token", required = false) String token,
            @RequestHeader(value = "List-Unsubscribe-Post", required = false) String listUnsubscribePost) {

        boolean oneClick = listUnsubscribePost != null
                && listUnsubscribePost.toLowerCase(Locale.ROOT).contains("list-unsubscribe=one-click");

        if (!oneClick) {
            return unsubscribeGet(token);
        }

        if (token == null || token.isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        try {
            unsubscribeService.unsubscribe(token);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            log.warn("unsubscribe.failed token={} msg={}", SensitiveValueMasker.truncateForLog(token, 12), e.toString());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
    }

    @Override
    public ResponseEntity<Void> unsubscribeGet(
            @RequestParam(value = "token", required = false) String token) {

        if (token == null || token.isBlank()) {
            return RedirectResponses.seeOther(frontendBaseUrl, frontendUnsubscribePath, "missing_token");
        }

        try {
            unsubscribeService.unsubscribe(token);
            return RedirectResponses.seeOther(frontendBaseUrl, frontendUnsubscribePath, null);
        } catch (Exception e) {
            log.warn("unsubscribe.failed token={} msg={}", SensitiveValueMasker.truncateForLog(token, 12), e.toString());
            return RedirectResponses.seeOther(frontendBaseUrl, frontendUnsubscribePath, "invalid_token");
        }
    }

    @Override
    public ResponseEntity<Void> subscribeUserAccount(
            @RequestParam(value = "token", required = false) String token) {

        if (token == null || token.isBlank()) {
            log.warn("subscribe.failed missing token");
            return ResponseEntity.badRequest().build();
        }

        try {
            subscribeService.resubscribe(token);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            log.warn("subscribe.failed token={} msg={}", SensitiveValueMasker.truncateForLog(token, 12), e.toString());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
    }

    @Override
    public ResponseEntity<SubscriptionStatusResponse> getSubscriptionStatus(
            @RequestParam(value = "email") String email) {

        if (email == null || email.isBlank()) {
            return ResponseEntity.badRequest().body(null);
        }

        String normalized = email.trim();
        if (normalized.isEmpty()) {
            return ResponseEntity.badRequest().body(null);
        }

        boolean suppressed = suppressionService.shouldBlock(normalized);
        SubscriptionStatusResponse response = new SubscriptionStatusResponse();
        response.setEmail(normalized);
        response.setSuppressed(suppressed);
        return ResponseEntity.ok(response);
    }
}

