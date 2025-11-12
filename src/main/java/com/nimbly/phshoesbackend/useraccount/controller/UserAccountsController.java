package com.nimbly.phshoesbackend.useraccount.controller;

import com.nimbly.phshoesbackend.useraccount.exception.AccountBlockedException;
import com.nimbly.phshoesbackend.useraccount.service.SuppressionService;
import com.nimbly.phshoesbackend.useraccounts.api.UserAccountsApi;
import com.nimbly.phshoesbackend.useraccounts.model.CreateUserAccountRequest;

import com.nimbly.phshoesbackend.useraccount.auth.JwtTokenProvider;
import com.nimbly.phshoesbackend.useraccount.exception.NotificationSendException;
import com.nimbly.phshoesbackend.useraccount.service.UserAccountsService;
import com.nimbly.phshoesbackend.useraccount.verification.VerificationService;
import com.nimbly.phshoesbackend.useraccounts.model.CreateUserAccountResponse;
import com.nimbly.phshoesbackend.useraccounts.model.TokenContentResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.NativeWebRequest;

@Slf4j
@RestController
@RequiredArgsConstructor
public class UserAccountsController implements UserAccountsApi {

    private final UserAccountsService accountService;
    private final VerificationService verificationService;
    private final SuppressionService suppressionService;
    private final JwtTokenProvider jwtTokenProvider;
    private final NativeWebRequest nativeWebRequest;

    // POST /api/v1/user-accounts
    @Override
    public ResponseEntity<CreateUserAccountResponse> createUserAccount(@Valid CreateUserAccountRequest request) {
        if(suppressionService.shouldBlock(request.getEmail())){
            log.warn("Attempted to create a blocked account with email {}", request.getEmail());
            throw new AccountBlockedException("Account is listed as blocked in suppression list");
        }

        CreateUserAccountResponse created = accountService.register(request);

        try {
            verificationService.sendVerificationEmail(request.getEmail());
        } catch (Exception ex) {
            log.warn("verification.send failed email={} err={}", created.getEmail(), ex.toString());
            log.warn("Rolling back user creation with userid={}", created.getUserid());
            accountService.deleteOwnAccount(created.getUserid());
            throw (ex instanceof NotificationSendException)
                    ? (NotificationSendException) ex
                    : new NotificationSendException("Verification pipeline failed", ex);
        }

        return ResponseEntity.status(201).body(created);
    }

    /**
     * Legacy alias for clients that still POST to /user-accounts/register.
     * Delegates to the canonical OpenAPI-generated handler above.
     */
    @PostMapping("/user-accounts/register")
    public ResponseEntity<CreateUserAccountResponse> createUserAccountLegacy(
            @Valid @RequestBody CreateUserAccountRequest request) {
        return createUserAccount(request);
    }

    @Override
    public ResponseEntity<TokenContentResponse> getTokenContent() {
        String authorizationHeader = nativeWebRequest.getHeader(HttpHeaders.AUTHORIZATION);
        TokenContentResponse body = accountService.getContentFromToken(authorizationHeader);
        return ResponseEntity.ok(body);
    }

    // DELETE /api/v1/user-accounts  (Bearer)
    @Override
    public ResponseEntity<Void> deleteMyAccount() {
        String authorizationHeader = nativeWebRequest.getHeader(HttpHeaders.AUTHORIZATION);
        String userId = jwtTokenProvider.userIdFromAuthorizationHeader(authorizationHeader);
        accountService.deleteOwnAccount(userId);
        log.info("user.delete completed userId={}", userId);
        return ResponseEntity.noContent().build();
    }
}
