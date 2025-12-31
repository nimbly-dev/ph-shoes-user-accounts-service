package com.nimbly.phshoesbackend.useraccount.web.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(prefix = "springdoc.api-docs", name = "enabled", havingValue = "true")
public class OpenApiConfig {

    @Bean
    public OpenAPI api() {
        String scheme = "BearerAuth"; // match case
        return new OpenAPI()
                .addSecurityItem(new SecurityRequirement().addList(scheme))
                .schemaRequirement(scheme, new SecurityScheme()
                        .name(scheme).type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT"));
    }
}
