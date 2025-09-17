package com.nimbly.phshoesbackend.useraccount.repository;

import com.nimbly.phshoesbackend.useraccount.model.Account;

import java.util.Optional;

public interface AccountRepository {
    Optional<Account> findById(String userId);
    Optional<Account> findByEmail(String email);
    Account save(Account account); // create/update

    void recordFailedLogin(String userIdOrEmail, int maxFailures, int lockSeconds);
    void recordSuccessfulLogin(String userId, String ip, String userAgent);

    void deleteById(String userId);
}
