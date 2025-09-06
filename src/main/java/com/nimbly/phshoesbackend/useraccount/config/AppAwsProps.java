package com.nimbly.phshoesbackend.useraccount.config;


import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "aws")
public class AppAwsProps {
    private String region;
    private String endpoint;
}
