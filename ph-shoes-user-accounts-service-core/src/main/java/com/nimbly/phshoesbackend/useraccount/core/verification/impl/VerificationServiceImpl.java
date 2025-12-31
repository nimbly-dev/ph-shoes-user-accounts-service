package com.nimbly.phshoesbackend.useraccount.core.verification.impl;

import com.nimbly.phshoesbackend.notification.core.model.dto.EmailRequest;
import com.nimbly.phshoesbackend.notification.core.model.dto.SendResult;
import com.nimbly.phshoesbackend.notification.core.model.props.NotificationEmailProps;
import com.nimbly.phshoesbackend.notification.core.service.NotificationService;
import com.nimbly.phshoesbackend.useraccount.core.model.Account;
import com.nimbly.phshoesbackend.commons.core.model.SuppressionReason;
import com.nimbly.phshoesbackend.useraccount.core.model.VerificationEntry;
import com.nimbly.phshoesbackend.useraccount.core.model.VerificationStatus;
import com.nimbly.phshoesbackend.useraccount.core.repository.AccountRepository;
import com.nimbly.phshoesbackend.useraccount.core.repository.VerificationRepository;
import com.nimbly.phshoesbackend.useraccount.core.config.props.AppVerificationProps;
import com.nimbly.phshoesbackend.notification.core.exception.NotificationSendException;
import com.nimbly.phshoesbackend.useraccount.core.exception.UserAccountNotificationSendException;
import com.nimbly.phshoesbackend.useraccount.core.exception.VerificationAlreadyUsedException;
import com.nimbly.phshoesbackend.useraccount.core.exception.VerificationExpiredException;
import com.nimbly.phshoesbackend.useraccount.core.exception.VerificationNotFoundException;
import com.nimbly.phshoesbackend.commons.core.security.EmailCrypto;
import com.nimbly.phshoesbackend.useraccount.core.service.SuppressionService;   
import com.nimbly.phshoesbackend.useraccount.core.unsubscribe.UnsubscribeService;
import com.nimbly.phshoesbackend.useraccount.core.util.SensitiveValueMasker;
import com.nimbly.phshoesbackend.useraccount.core.verification.VerificationService;
import com.nimbly.phshoesbackend.useraccount.core.verification.VerificationTokenCodec;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class VerificationServiceImpl implements VerificationService {

    private static final int MIN_TTL_SECONDS = 60;

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
        VerificationEmailContext context = VerificationEmailContextResolver.resolve(
                inputEmail,
                emailCrypto,
                accountRepository
        );

        log.info("verification.send start mode={} hashPrefix={}",
                context.providedHash() ? "hash" : "email",
                SensitiveValueMasker.hashPrefix(context.effectiveHash()));

        if (suppressionService.shouldBlock(context.normalizedEmail())) {
            log.warn("verification.suppressed email={}", SensitiveValueMasker.maskEmail(context.normalizedEmail()));
            return;
        }

        long nowEpochSeconds = Instant.now().getEpochSecond();
        long configuredTtl = verificationProps.getTtlSeconds();
        long ttlSeconds = Math.max(MIN_TTL_SECONDS, configuredTtl);
        long expiresAtEpochSeconds = nowEpochSeconds + ttlSeconds;

        String verificationId = UUID.randomUUID().toString();

        VerificationEntry pendingEntry = new VerificationEntry();
        pendingEntry.setVerificationId(verificationId);
        pendingEntry.setUserId(context.userId().orElse(null));
        pendingEntry.setEmailHash(context.effectiveHash());
        pendingEntry.setStatus(VerificationStatus.PENDING);
        pendingEntry.setExpiresAt(expiresAtEpochSeconds);
        pendingEntry.setCreatedAt(Instant.ofEpochSecond(nowEpochSeconds));

        verificationRepository.put(pendingEntry);

        log.info("verification.entry created id={} hashPrefix={} expiresAt={}",
                verificationId, SensitiveValueMasker.hashPrefix(pendingEntry.getEmailHash()), expiresAtEpochSeconds);

        String token = tokenCodec.encode(verificationId);
        EmailRequest emailRequest = VerificationEmailComposer.compose(
                context.normalizedEmail(),
                pendingEntry.getEmailHash(),
                token,
                emailProps,
                verificationProps,
                unsubscribeService
        );
        SendResult sendResult;
        try {
            sendResult = notificationService.sendEmailVerification(emailRequest);
            if (sendResult == null || sendResult.getAcceptedAt() == null) {
                throw new UserAccountNotificationSendException("Verification email was not accepted by provider");
            }
        } catch (NotificationSendException e) {
            log.error("Fail to dispatch email notification {}", e.getMessage());
            throw new UserAccountNotificationSendException("Failed to send verification email: " + e.getMessage(), e);
        } catch (UserAccountNotificationSendException e) {
            log.error("Fail to dispatch email notification {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            throw new UserAccountNotificationSendException("Failed to send verification email", e);
        }

        log.info("verification.sent provider={} messageId={} acceptedAt={} requestId={}",
                sendResult.getProvider(),
                sendResult.getMessageId(),
                sendResult.getAcceptedAt(),
                sendResult.getRequestId());
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

        boolean alreadyVerified = false;
        if (entry.getUserId() != null && !entry.getUserId().isBlank()) {
            alreadyVerified = accountRepository.findByUserId(entry.getUserId())
                    .map(Account::getIsVerified)
                    .map(Boolean::booleanValue)
                    .orElse(false);
        } else if (entry.getEmailHash() != null && !entry.getEmailHash().isBlank()) {
            alreadyVerified = accountRepository.findByEmailHash(entry.getEmailHash())
                    .map(Account::getIsVerified)
                    .map(Boolean::booleanValue)
                    .orElse(false);
        }

        if (entry.getStatus() != VerificationStatus.PENDING || alreadyVerified) {
            throw new VerificationAlreadyUsedException("Verification token already consumed");
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
            boolean alreadyVerifiedAfter = false;
            if (after.getUserId() != null && !after.getUserId().isBlank()) {
                alreadyVerifiedAfter = accountRepository.findByUserId(after.getUserId())
                        .map(Account::getIsVerified)
                        .map(Boolean::booleanValue)
                        .orElse(false);
            } else if (after.getEmailHash() != null && !after.getEmailHash().isBlank()) {
                alreadyVerifiedAfter = accountRepository.findByEmailHash(after.getEmailHash())
                        .map(Account::getIsVerified)
                        .map(Boolean::booleanValue)
                        .orElse(false);
            }

            if (after.getStatus() != VerificationStatus.PENDING || alreadyVerifiedAfter) {
                throw new VerificationAlreadyUsedException("Verification token already consumed");
            }
            // status somehow remained pending; retry once
            verificationRepository.markUsedIfPendingAndNotExpired(verificationId, nowEpochSeconds);
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
            log.warn("verification.not_me id={} emailHash={}", verificationId, SensitiveValueMasker.hashPrefix(emailHash));
            return true;
        }

        log.warn("verification.not_me id={} emailHash missing", verificationId);
        return false;
    }

}

