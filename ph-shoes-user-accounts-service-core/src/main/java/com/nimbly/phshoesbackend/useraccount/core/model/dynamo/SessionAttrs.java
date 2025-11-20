package com.nimbly.phshoesbackend.useraccount.core.model.dynamo;

public class SessionAttrs {
    private SessionAttrs() {}

    public static final String TABLE      = "login_sessions";
    public static final String PK_SESSION = "sessionId";
    public static final String USER_ID    = "userId";
    public static final String CREATED_AT = "createdAt";
    public static final String EXPIRES_AT = "expiresAt";   // epoch seconds (TTL)
    public static final String IP         = "ip";
    public static final String USER_AGENT = "userAgent";
    public static final String DATA_JSON  = "dataJson";    // optional blob
}
