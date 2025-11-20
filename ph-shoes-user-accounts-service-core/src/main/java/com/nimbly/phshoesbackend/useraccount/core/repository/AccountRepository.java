package com.nimbly.phshoesbackend.useraccount.core.repository;


import com.nimbly.phshoesbackend.useraccount.core.model.Account;

import java.util.Optional;

public interface AccountRepository {
    Optional<Account> findByUserId(String userId);

    Optional<Account> findByEmailHash(String emailHash);

    boolean existsByEmailHash(String emailHash);

    void save(Account account);

    void setVerified(String userId, boolean verified);

    void deleteByUserId(String userId);
}
