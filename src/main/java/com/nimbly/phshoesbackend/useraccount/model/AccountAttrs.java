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
}