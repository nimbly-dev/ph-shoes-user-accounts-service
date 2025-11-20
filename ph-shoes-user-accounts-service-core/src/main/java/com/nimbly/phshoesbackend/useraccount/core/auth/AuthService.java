package com.nimbly.phshoesbackend.useraccount.core.auth;

import com.nimbly.phshoesbackend.useraccounts.model.LoginRequest;
import com.nimbly.phshoesbackend.useraccounts.model.TokenResponse;

public interface AuthService {
    TokenResponse login(LoginRequest request, String ip, String userAgent);
    void logout(String authorizationHeader);
}
