package com.nimbly.phshoesbackend.useraccount.auth.dto;

import lombok.Data;


@Data
public class TokenResponse {
    private String token_type = "Bearer";
    private String access_token;
    private long expires_in;
}