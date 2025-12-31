package com.nimbly.phshoesbackend.useraccount.core.unsubscribe.impl;

import com.nimbly.phshoesbackend.notification.core.model.props.NotificationEmailProps;
import com.nimbly.phshoesbackend.commons.core.model.SuppressionReason;
import com.nimbly.phshoesbackend.useraccount.core.config.props.AppVerificationProps;
import com.nimbly.phshoesbackend.useraccount.core.service.SuppressionService;
import com.nimbly.phshoesbackend.useraccount.core.unsubscribe.UnsubscribeService;
import com.nimbly.phshoesbackend.useraccount.core.unsubscribe.UnsubscribeTokenCodec;
import com.nimbly.phshoesbackend.useraccount.core.util.SensitiveValueMasker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class UnsubscribeServiceImpl implements UnsubscribeService {

    private static final String SOURCE = "list-unsubscribe";
    private static final String NOTES = "Manual Unsubscribe";

    private final UnsubscribeTokenCodec unsubscribeTokenCodec;
    private final SuppressionService suppressionService;
    private final NotificationEmailProps emailProps;
    private final AppVerificationProps verificationProps;

    @Override
    public void unsubscribe(String token) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("token must not be blank");
        }
        String emailHash = unsubscribeTokenCodec.decodeAndVerify(token);
        suppressionService.suppressHash(
                emailHash,
                SuppressionReason.MANUAL,
                SOURCE,
                NOTES,
                null
        );
        log.info("unsubscribe.success hashPrefix={}", SensitiveValueMasker.hashPrefix(emailHash));
    }

    @Override
    public Optional<String> buildListUnsubscribeHeader(String emailHash) {
        if (emailHash == null || emailHash.isBlank()) {
            return Optional.empty();
        }

        Set<String> entries = new LinkedHashSet<>();
        String configured = emailProps.getListUnsubscribe();
        if (configured != null && !configured.isBlank()) {
            for (String raw : configured.split(",")) {
                String trimmed = raw.trim();
                if (trimmed.isEmpty()) {
                    continue;
                }
                entries.add(trimmed);
            }
        }

        String baseUrl = null;
        String unsubscribeLink = emailProps.getUnsubscribeLink();
        if (unsubscribeLink != null && !unsubscribeLink.isBlank()) {
            baseUrl = unsubscribeLink;
        } else {
            String verificationLink = verificationProps.getVerificationLink();
            if (verificationLink == null || verificationLink.isBlank()) {
                log.info("unsubscribe.derive_base skipped reason=missing_verification_link");
            } else {
                try {
                    URI base = URI.create(verificationLink);
                    String path = Optional.ofNullable(base.getPath()).orElse("/");
                    String prefix = path.replaceFirst("/verify.*", "/");
                    if (!prefix.endsWith("/")) {
                        prefix = prefix + "/";
                    }
                    URI rebuilt = new URI(
                            base.getScheme(),
                            base.getAuthority(),
                            prefix + "user-accounts/unsubscribe",
                            null,
                            null
                    );
                    baseUrl = rebuilt.toString();
                } catch (Exception e) {
                    log.warn("unsubscribe.base_derivation_failed link={} msg={}", verificationLink, e.toString());
                }
            }
        }

        if (baseUrl != null) {
            try {
                String tokenValue = unsubscribeTokenCodec.encode(emailHash);
                String separator = baseUrl.contains("?") ? "&" : "?";
                String url = baseUrl + separator + "token=" + URLEncoder.encode(tokenValue, StandardCharsets.UTF_8);
                entries.add("<" + url + ">");
                log.debug("unsubscribe.header_added type=one_click url={}", url);
            } catch (Exception e) {
                log.warn("unsubscribe.token_generation_failed hashPrefix={} msg={}",
                        SensitiveValueMasker.hashPrefix(emailHash), e.toString());
            }
        }

        if (entries.isEmpty()) {
            log.warn("unsubscribe.header_missing hashPrefix={}", SensitiveValueMasker.hashPrefix(emailHash));
            return Optional.empty();
        }

        String headerValue = String.join(", ", entries);
        log.debug("unsubscribe.header_built value={}", headerValue);
        return Optional.of(headerValue);
    }
}

