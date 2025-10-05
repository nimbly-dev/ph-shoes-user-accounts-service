package com.nimbly.phshoesbackend.useraccount.model;

public class SuppressionAttrs {
    public static final String TABLE = "email_suppressions";
    public static final String PK_EMAIL = "email";
    public static final String REASON = "reason";
    public static final String SOURCE = "source";
    public static final String NOTES = "notes";
    public static final String CREATED_AT = "created_at";
    public static final String EXPIRES_AT = "expires_at";

    private SuppressionAttrs() {
    }
}
