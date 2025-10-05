package com.nimbly.phshoesbackend.useraccount.model;

public final class AccountAttrs {
    private AccountAttrs() {}

    public static final String TABLE          = "accounts";
    public static final String GSI_EMAIL      = "gsi_email";
    public static final String PK_USERID      = "userid";
    public static final String EMAIL_HASH     = "email";
    public static final String EMAIL_ENC      = "email_enc";
    public static final String PASSWORD_HASH  = "password";
    public static final String IS_VERIFIED    = "isVerified";
    public static final String CREATED_AT     = "createdAt";
    public static final String UPDATED_AT     = "updatedAt";
    public static final String SETTINGS_JSON = "settings_json";
    public static final String LOGIN_FAIL_COUNT   = "loginFailCount";
    public static final String LOCK_UNTIL         = "lockUntil";
    public static final String LAST_LOGIN_AT      = "lastLoginAt";
    public static final String LAST_LOGIN_IP      = "lastLoginIp";
    public static final String LAST_LOGIN_UA      = "lastLoginUserAgent";
}