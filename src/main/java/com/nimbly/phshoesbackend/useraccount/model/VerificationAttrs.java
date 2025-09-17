package com.nimbly.phshoesbackend.useraccount.model;


public final class VerificationAttrs {
    private VerificationAttrs() {}

    public static final String TABLE              = "account_verifications";
    public static final String PK_VERIFICATION_ID = "verificationId";
    public static final String USER_ID            = "userId";
    public static final String EMAIL_HASH         = "emailHash";   // legacy/optional
    public static final String EMAIL_PLAIN        = "emailPlain";  // <-- used in verify response
    public static final String CODE_HASH          = "code";
    public static final String STATUS             = "status";
    public static final String EXPIRES_AT         = "expiresAt";   // Number (epoch seconds) + TTL
    public static final String CREATED_AT         = "createdAt";
    public static final String VERIFIED_AT        = "verifiedAt";
}