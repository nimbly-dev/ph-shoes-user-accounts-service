package com.nimbly.phshoesbackend.useraccount.controller;

import com.nimbly.phshoesbackend.useraccount.auth.JwtTokenProvider;
import com.nimbly.phshoesbackend.useraccount.exception.*;
import com.nimbly.phshoesbackend.useraccount.model.dto.AccountCreateRequest;
import com.nimbly.phshoesbackend.useraccount.model.dto.AccountResponse;
import com.nimbly.phshoesbackend.useraccount.model.dto.GetContentFromTokenResponse;
import com.nimbly.phshoesbackend.useraccount.service.SuppressionService;
import com.nimbly.phshoesbackend.useraccount.service.UserAccountsService;
import com.nimbly.phshoesbackend.useraccount.verification.VerificationService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;

@Slf4j
@RestController
@RequestMapping("/api/v1/user-accounts")
public class UserAccountsController {

    @Autowired
    private final UserAccountsService accountService;
    @Autowired
    private final VerificationService verificationService;
    @Autowired
    private final SuppressionService suppressionService;
    @Autowired
    private final JwtTokenProvider jwtTokenProvider;
    @Value("${app.frontend.base-url:}")
    private String frontendBaseUrl;
    @Value("${app.frontend.verify-path:/}")
    private String frontendVerifyPath;

    public UserAccountsController(UserAccountsService accountService, VerificationService verificationService, SuppressionService suppressionService, JwtTokenProvider jwtTokenProvider) {
        this.accountService = accountService;
        this.verificationService = verificationService;
        this.suppressionService = suppressionService;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<GetContentFromTokenResponse> getEmailFromToken(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authz) {
        var dto = accountService.getContentFromTokenBearer(authz);
        return ResponseEntity.ok(dto);
    }

    @PostMapping(consumes = "application/json", produces = "application/json")
    public ResponseEntity<AccountResponse> register(@Valid @RequestBody AccountCreateRequest body) {
        AccountResponse created = accountService.register(body);
        try {
            log.info("created user with id {}", created.getUserid());
            verificationService.sendVerificationEmail(created.getEmail());
        } catch (NotificationSendException ex) {
            log.warn("verification.send failed userId={} msg={}", created.getUserid(), ex.toString());
            throw ex;

        } catch (Exception ex) {
            log.error("register pipeline failed userId={} unexpected={}", created.getUserid(), ex.toString());
            accountService.deleteOwnAccount(created.getUserid());
            throw new NotificationSendException("Verification pipeline failed", ex);
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @DeleteMapping()
    public ResponseEntity<Void> deleteMyAccount(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {

        String userId = jwtTokenProvider.userIdFromAuthorizationHeader(authorization);
        accountService.deleteOwnAccount(userId);
        log.info("user.delete completed userId={}", userId);
        return ResponseEntity.noContent().build();
    }


    private ResponseEntity<Void> redirect(String base, String path, String code) {
        URI loc = UriComponentsBuilder.fromUriString(base).path(path).queryParam("verified", false).queryParam("error", code).build(true).toUri();
        return ResponseEntity.status(HttpStatus.SEE_OTHER).location(loc).build();
    }

    private ResponseEntity<Void> redirectResend(String base, String path, String code) {
        URI loc = UriComponentsBuilder.fromUriString(base).path(path).queryParam("resent", false).queryParam("error", code).build(true).toUri();
        return ResponseEntity.status(HttpStatus.SEE_OTHER).location(loc).build();
    }

    public record ResendRequest(String email) {
    }
}
