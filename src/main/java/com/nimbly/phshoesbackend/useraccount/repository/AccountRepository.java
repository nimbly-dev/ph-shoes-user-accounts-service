package com.nimbly.phshoesbackend.useraccount.repository;

import com.nimbly.phshoesbackend.useraccount.model.Account;
import com.nimbly.phshoesbackend.useraccount.model.dto.VerificationData;

import java.time.Instant;
import java.util.Optional;

public interface AccountRepository {
    Optional<Account> findById(String userId);
    Optional<Account> findByEmail(String email);
    Account save(Account account); // create/update

    void recordFailedLogin(String userIdOrEmail, int maxFailures, int lockSeconds);
    void recordSuccessfulLogin(String userId, String ip, String userAgent);

    void deleteById(String userId);

    void createSession(String jti, String userId, long expEpochSeconds, String ip, String ua);
    boolean isSessionActive(String jti);
    void revokeSession(String jti);

    void revokeAllSessionsForUser(String userid);
}
