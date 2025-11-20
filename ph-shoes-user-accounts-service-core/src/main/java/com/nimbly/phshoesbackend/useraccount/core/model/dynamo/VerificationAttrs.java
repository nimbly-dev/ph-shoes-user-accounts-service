package com.nimbly.phshoesbackend.useraccount.core.model.dynamo;


public final class VerificationAttrs {
    private VerificationAttrs() {}

    public static final String TABLE              = "account_verifications";
    public static final String PK_VERIFICATION_ID = "verificationId";
    public static final String USER_ID            = "userId";
    public static final String EMAIL_HASH         = "emailHash";     // HMAC(email)
    public static final String STATUS             = "status";
    public static final String EXPIRES_AT         = "expiresAt";     // epoch seconds (TTL)
    public static final String CREATED_AT         = "createdAt";
    public static final String VERIFIED_AT        = "verifiedAt";

    public static final String GSI_EMAIL          = "gsi_email";
}