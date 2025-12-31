package com.nimbly.phshoesbackend.useraccount.core.verification.impl;

import com.nimbly.phshoesbackend.commons.core.security.EmailCrypto;
import com.nimbly.phshoesbackend.useraccount.core.model.Account;
import com.nimbly.phshoesbackend.useraccount.core.repository.AccountRepository;
import com.nimbly.phshoesbackend.useraccount.core.util.SensitiveValueMasker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;

final class VerificationEmailContextResolver {
    private static final Logger log = LoggerFactory.getLogger(VerificationEmailContextResolver.class);

    private VerificationEmailContextResolver() {
    }

    static VerificationEmailContext resolve(String rawEmailOrHash,
                                            EmailCrypto emailCrypto,
                                            AccountRepository accountRepository) {
        if (rawEmailOrHash == null || rawEmailOrHash.isBlank()) {
            throw new IllegalArgumentException("email must not be blank");
        }

        if (rawEmailOrHash.contains("@")) {
            String normalizedEmail = emailCrypto.normalize(rawEmailOrHash);
            if (normalizedEmail == null || normalizedEmail.isBlank()) {
                throw new IllegalArgumentException("email must not be blank");
            }
            List<String> candidateHashes = emailCrypto.hashCandidates(normalizedEmail);
            if (candidateHashes == null || candidateHashes.isEmpty()) {
                throw new IllegalArgumentException("email must not be blank");
            }
            Optional<Account> matchingAccount = Optional.empty();
            for (String candidate : candidateHashes) {
                matchingAccount = accountRepository.findByEmailHash(candidate);
                if (matchingAccount.isPresent()) {
                    break;
                }
            }
            return new VerificationEmailContext(normalizedEmail, candidateHashes, matchingAccount, false);
        }

        Optional<Account> accountOpt = accountRepository.findByEmailHash(rawEmailOrHash);
        if (accountOpt.isEmpty()) {
            log.warn("verification.send hash_without_account hashPrefix={}", SensitiveValueMasker.hashPrefix(rawEmailOrHash));
            throw new IllegalArgumentException("Unknown email hash");
        }

        String decryptedEmail = emailCrypto.decrypt(accountOpt.get().getEmailEnc());
        String normalizedEmail = emailCrypto.normalize(decryptedEmail);
        if (normalizedEmail == null || normalizedEmail.isBlank()) {
            throw new IllegalArgumentException("email must not be blank");
        }
        List<String> candidateHashes = emailCrypto.hashCandidates(normalizedEmail);
        if (candidateHashes == null || candidateHashes.isEmpty()) {
            throw new IllegalArgumentException("email must not be blank");
        }
        return new VerificationEmailContext(normalizedEmail, candidateHashes, accountOpt, true);
    }
}

