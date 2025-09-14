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
    private String userid;                 // PK
    private String email;                  // GSI PK (login lookups)

    // auth
    private String password;
    private Boolean emailVerified;
    private Integer loginFailCount;        // <-- matches repo method names
    private Long lockUntil;

    // telemetry expected by repo
    private Instant lastLoginAt;           // <-- setLastLoginAt(Instant)
    private String  lastLoginIp;           // <-- setLastLoginIp(String)
    private String  lastLoginUserAgent;    // <-- setLastLoginUserAgent(String)

    // housekeeping
    private Instant createdAt;          // ISO-8601 string in Dynamo
    private Instant updatedAt;          // ISO-8601 string in Dynamo

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