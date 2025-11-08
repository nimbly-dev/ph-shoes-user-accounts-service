package com.nimbly.phshoesbackend.useraccount.verification.impl;

import com.nimbly.phshoesbackend.notification.core.model.dto.EmailAddress;
import com.nimbly.phshoesbackend.notification.core.model.dto.EmailRequest;
import com.nimbly.phshoesbackend.notification.core.model.dto.SendResult;
import com.nimbly.phshoesbackend.notification.core.model.props.NotificationEmailProps;
import com.nimbly.phshoesbackend.notification.core.service.NotificationService;
import com.nimbly.phshoesbackend.services.common.core.model.Account;
import com.nimbly.phshoesbackend.services.common.core.model.SuppressionReason;
import com.nimbly.phshoesbackend.services.common.core.model.VerificationEntry;
import com.nimbly.phshoesbackend.services.common.core.model.VerificationStatus;
import com.nimbly.phshoesbackend.services.common.core.repository.AccountRepository;
import com.nimbly.phshoesbackend.services.common.core.repository.VerificationRepository;
import com.nimbly.phshoesbackend.useraccount.config.props.AppVerificationProps;
import com.nimbly.phshoesbackend.useraccount.exception.NotificationSendException;
import com.nimbly.phshoesbackend.useraccount.exception.VerificationExpiredException;
import com.nimbly.phshoesbackend.useraccount.exception.VerificationNotFoundException;
import com.nimbly.phshoesbackend.useraccount.security.EmailCrypto;
import com.nimbly.phshoesbackend.useraccount.service.SuppressionService;
import com.nimbly.phshoesbackend.useraccount.unsubscribe.UnsubscribeService;
import com.nimbly.phshoesbackend.useraccount.verification.VerificationService;
import com.nimbly.phshoesbackend.useraccount.verification.VerificationTokenCodec;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;

import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class VerificationServiceImpl implements VerificationService {

    private static final int MIN_TTL_SECONDS = 60;
    private static final String EMAIL_CATEGORY_TAG = "verification";
    private static final String EMAIL_SUBJECT = "Verify your PH Shoes account";

    private final NotificationService notificationService;
    private final NotificationEmailProps emailProps;
    private final VerificationTokenCodec tokenCodec;
    private final SuppressionService suppressionService;
    private final VerificationRepository verificationRepository;
    private final AccountRepository accountRepository;
    private final AppVerificationProps verificationProps;
    private final EmailCrypto emailCrypto;
    private final UnsubscribeService unsubscribeService;

    /**
     * Workflow: resolve email -> create verification entry -> dispatch notification.
     */
    @Override
    public void sendVerificationEmail(String inputEmail) {
        VerificationEmailContext context = resolveEmailContext(inputEmail);

        log.info("verification.send start mode={} hashPrefix={}",
                context.providedHash() ? "hash" : "email",
                shortHash(context.effectiveHash()));

        if (suppressionService.shouldBlock(context.normalizedEmail())) {
            log.warn("verification.suppressed email={}", maskEmailAddress(context.normalizedEmail()));
            return;
        }

        long nowEpochSeconds = Instant.now().getEpochSecond();
        long configuredTtl = verificationProps.getTtlSeconds();
        long ttlSeconds = Math.max(MIN_TTL_SECONDS, configuredTtl);
        long expiresAtEpochSeconds = nowEpochSeconds + ttlSeconds;

        String verificationId = UUID.randomUUID().toString();

        VerificationEntry pendingEntry = buildPendingEntry(
                context,
                verificationId,
                nowEpochSeconds,
                expiresAtEpochSeconds
        );

        verificationRepository.put(pendingEntry);

        log.info("verification.entry created id={} hashPrefix={} expiresAt={}",
                verificationId, shortHash(pendingEntry.getEmailHash()), expiresAtEpochSeconds);

        String token = tokenCodec.encode(verificationId);
        EmailRequest emailRequest = buildEmailRequest(
                context.normalizedEmail(),
                pendingEntry.getEmailHash(),
                token
        );

        SendResult sendResult = dispatchVerification(emailRequest);

        log.info("verification.sent provider={} messageId={} acceptedAt={} requestId={}",
                sendResult.getProvider(),
                sendResult.getMessageId(),
                sendResult.getAcceptedAt(),
                sendResult.getRequestId());
    }

    private VerificationEmailContext resolveEmailContext(String rawEmailOrHash) {
        if (rawEmailOrHash == null || rawEmailOrHash.isBlank()) {
            throw new IllegalArgumentException("email must not be blank");
        }

        if (rawEmailOrHash.contains("@")) {
            String normalizedEmail = normalizeOrThrow(rawEmailOrHash);
            List<String> candidateHashes = hashesOrThrow(normalizedEmail);
            Optional<Account> matchingAccount = findAccountByHashes(candidateHashes);
            return new VerificationEmailContext(normalizedEmail, candidateHashes, matchingAccount, false);
        }

        return resolveFromHash(rawEmailOrHash);
    }

    private VerificationEmailContext resolveFromHash(String emailHash) {
        Optional<Account> accountOpt = accountRepository.findByEmailHash(emailHash);
        if (accountOpt.isEmpty()) {
            log.warn("verification.send hash_without_account hashPrefix={}", shortHash(emailHash));
            throw new IllegalArgumentException("Unknown email hash");
        }

        String decryptedEmail = emailCrypto.decrypt(accountOpt.get().getEmailEnc());
        String normalizedEmail = normalizeOrThrow(decryptedEmail);
        List<String> candidateHashes = hashesOrThrow(normalizedEmail);
        return new VerificationEmailContext(normalizedEmail, candidateHashes, accountOpt, true);
    }

    private String normalizeOrThrow(String email) {
        String normalized = emailCrypto.normalize(email);
        if (normalized == null || normalized.isBlank()) {
            throw new IllegalArgumentException("email must not be blank");
        }
        return normalized;
    }

    private List<String> hashesOrThrow(String normalizedEmail) {
        List<String> candidates = emailCrypto.hashCandidates(normalizedEmail);
        if (candidates == null || candidates.isEmpty()) {
            throw new IllegalArgumentException("email must not be blank");
        }
        return candidates;
    }

    private VerificationEntry buildPendingEntry(VerificationEmailContext context,
                                                String verificationId,
                                                long createdAtEpochSeconds,
                                                long expiresAtEpochSeconds) {
        VerificationEntry entry = new VerificationEntry();
        entry.setVerificationId(verificationId);
        entry.setUserId(context.userId().orElse(null));
        entry.setEmailHash(context.effectiveHash());
        entry.setStatus(VerificationStatus.PENDING);
        entry.setExpiresAt(expiresAtEpochSeconds);
        entry.setCreatedAt(Instant.ofEpochSecond(createdAtEpochSeconds));
        return entry;
    }

    private EmailRequest buildEmailRequest(String recipientEmail,
                                           String emailHash,
                                           String token) {

        String verificationLinkBase = required("verification.verificationLink", verificationProps.getVerificationLink());
        String notMeLinkBase = required("verification.notMeLink", verificationProps.getNotMeLink());

        String verificationUrl = buildUrl(verificationLinkBase, token);
        String notMeUrl = buildUrl(notMeLinkBase, token);

        String htmlTemplate = loadClasspath("email/verification.html");
        String renderedHtml = renderHtml(htmlTemplate, verificationUrl, notMeUrl);

        Optional<String> listUnsubscribeHeader = unsubscribeService.buildListUnsubscribeHeader(emailHash);

        var requestBuilder = EmailRequest.builder()
                .from(parseSenderHeader(emailProps.getFrom()))
                .to(EmailAddress.builder().address(recipientEmail).build())
                .subject(EMAIL_SUBJECT)
                .htmlBody(renderedHtml)
                .textBody("Verify your account: " + verificationUrl)
                .tag("category", EMAIL_CATEGORY_TAG)
                .requestIdHint("verify:" + shortHash(emailHash));

        listUnsubscribeHeader
                .filter(header -> !header.isBlank())
                .ifPresent(value -> requestBuilder.header("List-Unsubscribe", value));
        if (emailProps.getListUnsubscribePost() != null
                && !emailProps.getListUnsubscribePost().isBlank()) {
            requestBuilder.header("List-Unsubscribe-Post", emailProps.getListUnsubscribePost());
        }

        return requestBuilder.build();
    }

    private SendResult dispatchVerification(EmailRequest request) {
        try {
            SendResult result = notificationService.sendEmailVerification(request);
            if (result == null || result.getAcceptedAt() == null) {
                throw new NotificationSendException("Verification email was not accepted by provider");
            }
            return result;
        } catch (NotificationSendException e) {
            log.error("Fail to dispatch email notification {}", e.getMessage());
            throw e; // bubble up exact cause
        } catch (Exception e) {
            throw new NotificationSendException("Failed to send verification email", e);
        }
    }


    @Override
    public void resendVerification(String emailPlain) {
        sendVerificationEmail(emailPlain);
    }

    @Override
    public boolean verify(String token) {
        String verificationId = tokenCodec.decodeAndVerify(token);
        long nowEpochSeconds = Instant.now().getEpochSecond();

        VerificationEntry entry = verificationRepository
                .getById(verificationId, true)
                .orElseThrow(() -> new VerificationNotFoundException("id=" + verificationId));

        if (entry.getExpiresAt() != null && entry.getExpiresAt() <= nowEpochSeconds) {
            throw new VerificationExpiredException("expired");
        }

        if (entry.getStatus() != VerificationStatus.PENDING) {
            return entry.getStatus() == VerificationStatus.VERIFIED;
        }

        try {
            verificationRepository.markUsedIfPendingAndNotExpired(verificationId, nowEpochSeconds);
        } catch (ConditionalCheckFailedException concurrencyRace) {
            VerificationEntry after = verificationRepository
                    .getById(verificationId, true)
                    .orElseThrow(() -> new VerificationNotFoundException("id=" + verificationId));

            if (after.getExpiresAt() != null && after.getExpiresAt() <= nowEpochSeconds) {
                throw new VerificationExpiredException("expired");
            }
            return after.getStatus() == VerificationStatus.VERIFIED;
        }

        // Mark account verified (late-bind by hash if necessary)
        String userId = entry.getUserId();
        if (userId == null || userId.isBlank()) {
            accountRepository.findByEmailHash(entry.getEmailHash())
                    .ifPresent(acc -> accountRepository.setVerified(acc.getUserId(), true));
        } else {
            accountRepository.setVerified(userId, true);
        }

        log.info("verification.verified id={} userId={}",
                verificationId, (userId == null ? "(late-bound)" : userId));

        return true;
    }

    @Override
    public boolean notMe(String token) {
        String verificationId = tokenCodec.decodeAndVerify(token);

        VerificationEntry entry = verificationRepository
                .getById(verificationId, true)
                .orElseThrow(() -> new VerificationNotFoundException("id=" + verificationId));

        if (entry.getStatus() == VerificationStatus.PENDING) {
            try {
                verificationRepository.markStatusIfPending(verificationId, VerificationStatus.FAILED);
                log.info("verification.not_me marked_failed id={}", verificationId);
            } catch (ConditionalCheckFailedException ignored) {
                log.debug("verification.not_me status_changed_before_update id={}", verificationId);
            }
        }

        // Prefer hash-based suppression (no plaintext storage)
        String emailHash = entry.getEmailHash();
        if (emailHash != null && !emailHash.isBlank()) {
            suppressionService.suppressHash(
                    emailHash,
                    SuppressionReason.COMPLAINT,
                    "verification",
                    "User clicked 'Not me'",
                    null
            );
            log.warn("verification.not_me id={} emailHash={}", verificationId, shortHash(emailHash));
            return true;
        }

        log.warn("verification.not_me id={} emailHash missing", verificationId);
        return false;
    }

    // ===== helpers =====

    private static String buildUrl(String baseUrl, String token) {
        String separator = baseUrl.contains("?") ? "&" : "?";
        return baseUrl + separator + "token=" + URLEncoder.encode(token, StandardCharsets.UTF_8);
    }

    private static String required(String key, String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing required property: " + key);
        }
        return value;
    }

    private static EmailAddress parseSenderHeader(String fromHeader) {
        String senderDisplayName = null;
        String senderAddress = fromHeader.trim();

        int openAngleIndex = fromHeader.indexOf('<');
        int closeAngleIndex = fromHeader.indexOf('>');

        if (openAngleIndex >= 0 && closeAngleIndex > openAngleIndex) {
            // Everything before '<' is (potentially quoted) display name
            senderDisplayName = stripEnclosingQuotes(fromHeader.substring(0, openAngleIndex).trim());
            // Everything between '<' and '>' is the address
            senderAddress = fromHeader.substring(openAngleIndex + 1, closeAngleIndex).trim();
        }

        return EmailAddress.builder()
                .name(isBlank(senderDisplayName) ? null : senderDisplayName)
                .address(senderAddress)
                .build();
    }

    private static String loadClasspath(String path) {
        try (InputStream in = new ClassPathResource(path).getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("Cannot load template: " + path, e);
        }
    }

    private static String renderHtml(String template, String verifyUrl, String notMeUrl) {
        return template
                .replace("{{VERIFY_URL}}", verifyUrl)
                .replace("${VERIFY_URL}", verifyUrl)
                .replace("{{NOT_ME_URL}}", notMeUrl)
                .replace("${NOT_ME_URL}", notMeUrl);
    }

    /**
     * Masks an email for logs: first char + "***" + domain (or "***" if no local part).
     */
    private static String maskEmailAddress(String emailAddress) {
        if (emailAddress == null || emailAddress.isBlank()) {
            return "(blank)";
        }

        int atIndex = emailAddress.indexOf('@');
        if (atIndex <= 1) { // 0 or 1 chars before '@' (or no '@' at all -> atIndex == -1)
            String domainPart = (atIndex >= 0) ? emailAddress.substring(atIndex) : "";
            return "***" + domainPart;
        }

        char firstLocalChar = emailAddress.charAt(0);
        String domainPart = (atIndex >= 0) ? emailAddress.substring(atIndex) : "";
        return firstLocalChar + "***" + domainPart;
    }

    private Optional<Account> findAccountByHashes(List<String> hashes) {
        return hashes.stream()
                .map(accountRepository::findByEmailHash)
                .flatMap(Optional::stream)
                .findFirst();
    }

    private static String shortHash(String hash) {
        if (hash == null || hash.isBlank()) {
            return "(blank)";
        }
        return hash.length() <= 8 ? hash : hash.substring(0, 8);
    }

    private static String stripEnclosingQuotes(String s) {
        if (s == null || s.length() < 2) return s;
        char first = s.charAt(0);
        char last = s.charAt(s.length() - 1);
        if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
            return s.substring(1, s.length() - 1);
        }
        return s;
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}
