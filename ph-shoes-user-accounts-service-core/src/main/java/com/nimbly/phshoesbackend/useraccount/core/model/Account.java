package com.nimbly.phshoesbackend.useraccount.core.model;

import com.nimbly.phshoesbackend.useraccount.core.model.dynamo.AccountAttrs;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.*;

@Data
@NoArgsConstructor
@DynamoDbBean
public class Account {

    @Getter(onMethod_ = {
            @DynamoDbPartitionKey,
            @DynamoDbAttribute(AccountAttrs.PK_USERID)
    })
    @Setter
    private String userId;

    // HMAC(email) stored under attribute name "email"; GSI = gsi_email
    @Getter(onMethod_ = {
            @DynamoDbSecondaryPartitionKey(indexNames = AccountAttrs.GSI_EMAIL),
            @DynamoDbAttribute(AccountAttrs.EMAIL_HASH)
    })
    @Setter
    private String emailHash;

    // AES-GCM ciphertext (Base64)
    @Getter(onMethod_ = {
            @DynamoDbAttribute(AccountAttrs.EMAIL_ENC)
    })
    @Setter
    private String emailEnc;

    @Getter(onMethod_ = {
            @DynamoDbAttribute(AccountAttrs.PASSWORD_HASH)
    })
    @Setter
    private String passwordHash;

    @Getter(onMethod_ = {
            @DynamoDbAttribute(AccountAttrs.IS_VERIFIED)
    })
    @Setter
    private Boolean isVerified;

    @Getter(onMethod_ = {
            @DynamoDbAttribute(AccountAttrs.CREATED_AT)
    })
    @Setter
    private Instant createdAt;

    @Getter(onMethod_ = {
            @DynamoDbAttribute(AccountAttrs.UPDATED_AT)
    })
    @Setter
    private Instant updatedAt;

    // optional fields present in AccountAttrs
    @Getter(onMethod_ = {
            @DynamoDbAttribute(AccountAttrs.SETTINGS_JSON)
    })
    @Setter
    private String settingsJson;

    @Getter(onMethod_ = {
            @DynamoDbAttribute(AccountAttrs.LOGIN_FAIL_COUNT)
    })
    @Setter
    private Integer loginFailCount;

    @Getter(onMethod_ = {
            @DynamoDbAttribute(AccountAttrs.LOCK_UNTIL)
    })
    @Setter
    private Instant lockUntil;

    @Getter(onMethod_ = {
            @DynamoDbAttribute(AccountAttrs.LAST_LOGIN_AT)
    })
    @Setter
    private Instant lastLoginAt;

    @Getter(onMethod_ = {
            @DynamoDbAttribute(AccountAttrs.LAST_LOGIN_IP)
    })
    @Setter
    private String lastLoginIp;

    @Getter(onMethod_ = {
            @DynamoDbAttribute(AccountAttrs.LAST_LOGIN_UA)
    })
    @Setter
    private String lastLoginUserAgent;
}