package com.nimbly.phshoesbackend.useraccount.core.service.impl;

import com.nimbly.phshoesbackend.services.common.core.model.SuppressionEntry;
import com.nimbly.phshoesbackend.services.common.core.model.SuppressionReason;
import com.nimbly.phshoesbackend.services.common.core.repository.SuppressionRepository;
import com.nimbly.phshoesbackend.services.common.core.security.EmailCrypto;
import com.nimbly.phshoesbackend.useraccount.core.service.SuppressionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SuppressionServiceImpl implements SuppressionService {

    private final SuppressionRepository repo;
    private final EmailCrypto emailCrypto;

    @Override
    public boolean shouldBlock(String emailPlain) {
        String normalized = emailCrypto.normalize(emailPlain);
        if (normalized == null || normalized.isBlank()) {
            return false;
        }
        List<String> hashes = emailCrypto.hashCandidates(normalized);
        if (hashes == null || hashes.isEmpty()) {
            return false;
        }
        boolean blocked = hashes.stream().anyMatch(repo::isSuppressed);
        if (blocked) {
            log.info("suppression.blocked email={}", mask(normalized));
        }
        return blocked;
    }

    @Override
    public void suppress(String emailPlain,
                         SuppressionReason reason,
                         String source,
                         String notes,
                         Long ttlEpochSeconds) {
        String normalized = emailCrypto.normalize(emailPlain);
        if (normalized == null || normalized.isBlank()) {
            return;
        }
        List<String> hashes = emailCrypto.hashCandidates(normalized);
        if (hashes == null || hashes.isEmpty()) {
            return;
        }
        suppressHash(hashes.get(0), reason, source, notes, ttlEpochSeconds);
    }

    @Override
    public void suppressHash(String emailHash,
                             SuppressionReason reason,
                             String source,
                             String notes,
                             Long ttlEpochSeconds) {
        SuppressionEntry entry = new SuppressionEntry();
        entry.setEmailHash(emailHash);
        entry.setReason(reason);
        entry.setSource(source);
        entry.setNotes(notes);
        entry.setCreatedAt(Instant.now());
        entry.setExpiresAt(ttlEpochSeconds);
        repo.put(entry);
        log.info("suppression.added reason={} hashPrefix={} source={}", reason, shortHash(emailHash), source);
    }

    @Override
    public void unsuppress(String emailPlain) {
        String normalized = emailCrypto.normalize(emailPlain);
        if (normalized == null || normalized.isBlank()) {
            return;
        }
        List<String> hashes = emailCrypto.hashCandidates(normalized);
        if (hashes == null || hashes.isEmpty()) {
            return;
        }
        hashes.forEach(repo::remove);
        log.info("suppression.removed email={}", mask(normalized));
    }

    @Override
    public void unsuppressHash(String emailHash) {
        repo.remove(emailHash);
        log.info("suppression.removed hashPrefix={}", shortHash(emailHash));
    }

    private static String mask(String email) {
        if (email == null || email.isBlank()) {
            return "(blank)";
        }
        int at = email.indexOf('@');
        if (at <= 1) {
            return "***" + (at >= 0 ? email.substring(at) : "");
        }
        return email.charAt(0) + "***" + (at >= 0 ? email.substring(at) : "");
    }

    private static String shortHash(String hash) {
        if (hash == null || hash.isBlank()) {
            return "(blank)";
        }
        return hash.length() <= 8 ? hash : hash.substring(0, 8);
    }
}
