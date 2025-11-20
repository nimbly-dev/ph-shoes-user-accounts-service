package com.nimbly.phshoesbackend.useraccount.core.model;

import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.*;

@Data
@NoArgsConstructor
@DynamoDbBean
public class SessionItem {

    @Getter(onMethod_ = {
            @DynamoDbPartitionKey,
            @DynamoDbAttribute("sessionId")
    })
    @Setter
    private String sessionId;

    @Getter(onMethod_ = {
            @DynamoDbAttribute("userId")
    })
    @Setter
    private String userId;

    @Getter(onMethod_ = {
            @DynamoDbAttribute("createdAt")
    })
    @Setter
    private Instant createdAt;

    // TTL (epoch seconds)
    @Getter(onMethod_ = {
            @DynamoDbAttribute("expiresAt")
    })
    @Setter
    private Long expiresAt;

    @Getter(onMethod_ = {
            @DynamoDbAttribute("ip")
    })
    @Setter
    private String ip;

    @Getter(onMethod_ = {
            @DynamoDbAttribute("userAgent")
    })
    @Setter
    private String userAgent;

    @Getter(onMethod_ = {
            @DynamoDbAttribute("dataJson")
    })
    @Setter
    private String dataJson;
}