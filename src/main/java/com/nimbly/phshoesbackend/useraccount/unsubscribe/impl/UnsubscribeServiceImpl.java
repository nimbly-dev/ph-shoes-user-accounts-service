package com.nimbly.phshoesbackend.useraccount.unsubscribe.impl;

import com.nimbly.phshoesbackend.notification.core.model.props.NotificationEmailProps;
import com.nimbly.phshoesbackend.services.common.core.model.SuppressionReason;
import com.nimbly.phshoesbackend.useraccount.config.props.AppVerificationProps;
import com.nimbly.phshoesbackend.useraccount.service.SuppressionService;
import com.nimbly.phshoesbackend.useraccount.unsubscribe.UnsubscribeService;
import com.nimbly.phshoesbackend.useraccount.unsubscribe.UnsubscribeTokenCodec;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.Locale;
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
        String emailHash = decodeToken(token);
        suppressionService.suppressHash(
                emailHash,
                SuppressionReason.MANUAL,
                SOURCE,
                NOTES,
                null
        );
        log.info("unsubscribe.success hashPrefix={}", shortHash(emailHash));
    }

    @Override
    public Optional<String> buildListUnsubscribeHeader(String emailHash) {
        if (emailHash == null || emailHash.isBlank()) {
            return Optional.empty();
        }

        Set<String> entries = new LinkedHashSet<>();
        collectMailtoEntries(entries);

        generateUnsubscribeUrl(emailHash).ifPresent(url -> {
            entries.add("<" + url + ">");
            log.debug("unsubscribe.header_added type=one_click url={}", url);
        });

        if (entries.isEmpty()) {
            log.warn("unsubscribe.header_missing hashPrefix={}", shortHash(emailHash));
            return Optional.empty();
        }

        String headerValue = String.join(", ", entries);
        log.debug("unsubscribe.header_built value={}", headerValue);
        return Optional.of(headerValue);
    }

    private String decodeToken(String token) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("token must not be blank");
        }
        return unsubscribeTokenCodec.decodeAndVerify(token);
    }

    private void collectMailtoEntries(Set<String> entries) {
        String configured = emailProps.getListUnsubscribe();
        if (configured == null || configured.isBlank()) {
            return;
        }
        for (String raw : configured.split(",")) {
            String trimmed = raw.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            if (isHttpLink(trimmed) && !isMailtoLink(trimmed)) {
                log.debug("unsubscribe.drop_legacy_http entry={}", trimmed);
                continue;
            }
            entries.add(trimmed);
        }
    }

    private Optional<String> generateUnsubscribeUrl(String emailHash) {
        String baseUrl = firstNonBlank(
                verificationProps.getUnsubscribeLink(),
                deriveUnsubscribeBaseFromVerificationLink());
        if (baseUrl == null) {
            return Optional.empty();
        }
        try {
            String token = unsubscribeTokenCodec.encode(emailHash);
            return Optional.of(buildUrl(baseUrl, token));
        } catch (Exception e) {
            log.warn("unsubscribe.token_generation_failed hashPrefix={} msg={}",
                    shortHash(emailHash), e.toString());
            return Optional.empty();
        }
    }

    private String deriveUnsubscribeBaseFromVerificationLink() {
        String verificationLink = verificationProps.getVerificationLink();
        if (verificationLink == null || verificationLink.isBlank()) {
            log.info("unsubscribe.derive_base skipped reason=missing_verification_link");
            return null;
        }
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
            return rebuilt.toString();
        } catch (Exception e) {
            log.warn("unsubscribe.base_derivation_failed link={} msg={}",
                    verificationLink, e.toString());
            return null;
        }
    }

    private static String firstNonBlank(String primary, String fallback) {
        if (primary != null && !primary.isBlank()) {
            return primary;
        }
        if (fallback != null && !fallback.isBlank()) {
            return fallback;
        }
        return null;
    }

    private static String buildUrl(String baseUrl, String token) {
        String separator = baseUrl.contains("?") ? "&" : "?";
        return baseUrl + separator + "token=" + URLEncoder.encode(token, StandardCharsets.UTF_8);
    }

    private static boolean isHttpLink(String part) {
        String lower = part.toLowerCase(Locale.ROOT);
        return lower.contains("http://") || lower.contains("https://");
    }

    private static boolean isMailtoLink(String part) {
        return part.toLowerCase(Locale.ROOT).contains("mailto:");
    }

    private static String shortHash(String hash) {
        if (hash == null || hash.isBlank()) {
            return "(blank)";
        }
        return hash.length() <= 8 ? hash : hash.substring(0, 8);
    }
}
