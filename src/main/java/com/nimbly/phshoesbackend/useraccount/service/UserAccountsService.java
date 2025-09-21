package com.nimbly.phshoesbackend.useraccount.service;

import com.nimbly.phshoesbackend.useraccount.model.dto.AccountCreateRequest;
import com.nimbly.phshoesbackend.useraccount.model.dto.AccountResponse;
import com.nimbly.phshoesbackend.useraccount.model.dto.GetContentFromTokenResponse;

public interface UserAccountsService {
    AccountResponse register(AccountCreateRequest request);
    AccountResponse verifyByToken(String token);
    void resendVerification(String email);
    void deleteOwnAccount(String userId);
    GetContentFromTokenResponse getContentFromTokenBearer(String authorizationHeader);
}
