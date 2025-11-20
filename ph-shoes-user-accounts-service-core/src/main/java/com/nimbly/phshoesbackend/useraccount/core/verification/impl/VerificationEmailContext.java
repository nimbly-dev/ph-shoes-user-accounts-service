package com.nimbly.phshoesbackend.useraccount.core.verification.impl;

import com.nimbly.phshoesbackend.useraccount.core.model.Account;

import java.util.List;
import java.util.Optional;

record VerificationEmailContext(
        String normalizedEmail,
        List<String> hashCandidates,
        Optional<Account> account,
        boolean providedHash
) {
    VerificationEmailContext {
        hashCandidates = List.copyOf(hashCandidates);
        account = account == null ? Optional.empty() : account;
    }

    String primaryHash() {
        return hashCandidates.get(0);
    }

    String effectiveHash() {
        return account.map(Account::getEmailHash).orElse(primaryHash());
    }

    Optional<String> userId() {
        return account.map(Account::getUserId);
    }

    boolean hasAccount() {
        return account.isPresent();
    }
}
