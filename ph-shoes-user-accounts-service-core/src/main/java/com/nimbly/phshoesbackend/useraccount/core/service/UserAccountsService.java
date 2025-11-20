package com.nimbly.phshoesbackend.useraccount.core.service;

import com.nimbly.phshoesbackend.useraccounts.model.CreateUserAccountRequest;
import com.nimbly.phshoesbackend.useraccounts.model.CreateUserAccountResponse;
import com.nimbly.phshoesbackend.useraccounts.model.TokenContentResponse;

public interface UserAccountsService {
    CreateUserAccountResponse register(CreateUserAccountRequest request);
    TokenContentResponse getContentFromToken(String authorizationHeader);
    void deleteOwnAccount(String userId);
}
