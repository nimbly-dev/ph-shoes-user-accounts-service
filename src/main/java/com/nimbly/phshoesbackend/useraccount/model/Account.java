package com.nimbly.phshoesbackend.useraccount.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbAttribute;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondaryPartitionKey;

import java.time.Instant;

@DynamoDbBean
@Data
@NoArgsConstructor
public class Account {

    // keys
    private String userid;
    private String email;

    // auth
    private String password;
    private Boolean emailVerified;
    private Integer loginFailCount;
    private Long lockUntil;

    // telemetry expected by repo
    private Instant lastLoginAt;
    private String  lastLoginIp;
    private String  lastLoginUserAgent;

    // housekeeping
    private Instant createdAt;
    private Instant updatedAt;

    // ---- Key / attribute name overrides on GETTERS ----
    @DynamoDbPartitionKey
    @DynamoDbAttribute("userid")        // change to "userId" if your attribute is camel-case
    public String getUserid() { return userid; }

    @DynamoDbSecondaryPartitionKey(indexNames = "gsi_email")
    @DynamoDbAttribute("email")
    public String getEmail() { return email; }

    @DynamoDbAttribute("isVerified")
    public Boolean getEmailVerified() { return emailVerified; }
    public void setEmailVerified(Boolean v) { this.emailVerified = v; }

}