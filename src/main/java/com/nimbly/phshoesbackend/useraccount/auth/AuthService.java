package com.nimbly.phshoesbackend.useraccount.auth;

import com.nimbly.phshoesbackend.useraccount.auth.dto.LoginRequest;
import com.nimbly.phshoesbackend.useraccount.auth.dto.TokenResponse;

public interface AuthService {
    TokenResponse login(LoginRequest request, String ip, String userAgent);
    void logout(String authorizationHeader);
}
