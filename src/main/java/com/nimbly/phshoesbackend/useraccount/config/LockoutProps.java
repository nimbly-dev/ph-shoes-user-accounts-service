package com.nimbly.phshoesbackend.useraccount.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "auth.lock")
public class LockoutProps {
    private int maxFailures = 5;
    private int durationSeconds = 900;
}
