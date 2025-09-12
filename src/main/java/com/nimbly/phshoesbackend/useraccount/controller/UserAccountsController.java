package com.nimbly.phshoesbackend.useraccount.controller;

import com.nimbly.phshoesbackend.useraccount.exception.EmailAlreadyRegisteredException;
import com.nimbly.phshoesbackend.useraccount.model.dto.AccountCreateRequest;
import com.nimbly.phshoesbackend.useraccount.model.dto.AccountResponse;
import com.nimbly.phshoesbackend.useraccount.service.UserAccountsService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/user-accounts")
public class UserAccountsController {

    @Autowired
    private final UserAccountsService accountService;

    public UserAccountsController(UserAccountsService accountService) {
        this.accountService = accountService;
    }

    @PostMapping(value = "/register",
            consumes = "application/json",
            produces = "application/json")
    public ResponseEntity<AccountResponse> register(@Valid @RequestBody AccountCreateRequest body) {
        var created = accountService.register(body);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    // NEW: verify by token (link click)
    @GetMapping("/verify")
    public ResponseEntity<AccountResponse> verify(@RequestParam("token") String token) {
        return ResponseEntity.ok(accountService.verifyByToken(token));
    }

    // NEW: resend verification (always 202; no email enumeration)
    @PostMapping("/verification/resend")
    public ResponseEntity<Void> resend(@RequestBody ResendRequest req) {
        accountService.resendVerification(req.email());
        return ResponseEntity.accepted().build();
    }

    public record ResendRequest(String email) {}
}
