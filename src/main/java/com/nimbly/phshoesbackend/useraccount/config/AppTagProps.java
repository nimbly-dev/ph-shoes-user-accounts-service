package com.nimbly.phshoesbackend.useraccount.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "app")
public class AppTagProps {
    private String projectTag;
    private String envTag;
    private String serviceTag;
}
