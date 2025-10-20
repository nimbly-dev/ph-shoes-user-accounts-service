package com.nimbly.phshoesbackend.useraccount.service.impl;

import com.nimbly.phshoesbackend.services.common.core.model.SuppressionEntry;
import com.nimbly.phshoesbackend.services.common.core.model.SuppressionReason;
import com.nimbly.phshoesbackend.services.common.core.repository.SuppressionRepository;
import com.nimbly.phshoesbackend.useraccount.service.SuppressionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class SuppresionServiceImpl implements SuppressionService {

    @Autowired
    private SuppressionRepository repo;

    @Override
    public boolean shouldBlock(String email) {
        return repo.isSuppressed(email.toLowerCase().trim());
    }

    @Override
    public void suppress(String email, SuppressionReason reason, String source, String notes, Long ttlEpochSeconds) {
        var entry = SuppressionEntry.builder()
                .email(email.toLowerCase().trim())
                .reason(reason)
                .source(source)
                .notes(notes)
                .createdAt(java.time.Instant.now())
                .expiresAt(ttlEpochSeconds) // nullable
                .build();
        repo.put(entry);
    }

    @Override
    public void unsuppress(String email) {
        repo.remove(email.toLowerCase().trim());
    }
}
