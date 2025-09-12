package com.nimbly.phshoesbackend.useraccount.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "verification")
public class AppVerificationProps {
    private int ttlSeconds = 900;
    private String secret;
    private String linkBaseUrl;
}
