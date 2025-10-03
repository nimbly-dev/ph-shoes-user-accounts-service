package com.nimbly.phshoesbackend.useraccount.config.props;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "jwt")
public class AppAuthProps {
    private String issuer;
    private String secret;
    private int accessTtlSeconds = 1800; //default 30m
}
