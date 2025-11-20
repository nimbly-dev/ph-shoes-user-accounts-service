package com.nimbly.phshoesbackend.useraccount.core.repository;

import java.util.List;

public interface SessionRepository {
    void createSession(String sessionId, String userId, long expiresAtEpochSec, String ip, String userAgent);
    boolean isSessionActive(String sessionId);
    void revokeSession(String sessionId);
    List<String> listActiveSessionIdsByUser(String userId, int limit);
}
