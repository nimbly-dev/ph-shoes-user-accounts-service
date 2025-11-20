package com.nimbly.phshoesbackend.useraccount.core.config.props;

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
