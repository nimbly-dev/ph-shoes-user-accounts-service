package com.nimbly.phshoesbackend.useraccount.config;

import com.nimbly.phshoesbackend.useraccount.auth.JwtAuthFilter;
import com.nimbly.phshoesbackend.useraccount.config.props.CorsProps;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
// import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final CorsProps corsProps;
    private final JwtAuthFilter jwtAuthFilter;

    public SecurityConfig(CorsProps corsProps, JwtAuthFilter jwtAuthFilter) {
        this.corsProps = corsProps;
        this.jwtAuthFilter = jwtAuthFilter;
    }


    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, JwtAuthFilter jwtAuthFilter) throws Exception {
        http
                .cors(Customizer.withDefaults())
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // CORS preflight
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                        // Public endpoints (account creation)
                        .requestMatchers(HttpMethod.POST, "/user-accounts").permitAll()
                        .requestMatchers(HttpMethod.POST, "/user-accounts/register").permitAll()
                        .requestMatchers(HttpMethod.POST, "/accounts").permitAll()

                        // SES -> SNS webhooks (signature + topic validation executed in SesWebhookProcessor)
                        .requestMatchers(HttpMethod.POST, "/internal/webhooks/ses").permitAll()

                        // Public endpoints (account unsubscribe/resubscribe/status)
                        .requestMatchers("/user-accounts/unsubscribe").permitAll()
                        .requestMatchers("/user-accounts/subscribe").permitAll()
                        .requestMatchers(HttpMethod.GET, "/user-accounts/subscription-status").permitAll()

                        // Public verification endpoints (with & without context-path)
                        .requestMatchers(HttpMethod.GET, "/verify/email").permitAll()
                        .requestMatchers(HttpMethod.POST, "/verify/email/resend").permitAll()
                        .requestMatchers(HttpMethod.GET, "/verify/email/not-me").permitAll()

                        // Public endpoints (login)
                        .requestMatchers(HttpMethod.POST, "/auth/login").permitAll()

                        // Swagger & health
                        .requestMatchers(
                                "/v3/api-docs/**",
                                "/swagger-ui.html",
                                "/swagger-ui/**",
                                "/actuator/health"
                        ).permitAll()
                        .requestMatchers(HttpMethod.GET, "/system/status").permitAll()

                        // Everything else
                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable);

        return http.build();
    }


    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration c = new CorsConfiguration();
        List<String> origins = corsProps.getAllowedOrigins();
        if (origins != null && !origins.isEmpty()) {
            c.setAllowedOrigins(origins); // use setAllowedOriginPatterns for wildcard subdomains
        }
        c.setAllowedMethods(corsProps.getAllowedMethods());
        c.setAllowedHeaders(corsProps.getAllowedHeaders());
        c.setExposedHeaders(corsProps.getExposedHeaders());
        c.setAllowCredentials(Boolean.TRUE.equals(corsProps.getAllowCredentials()));
        c.setMaxAge(corsProps.getMaxAge());

        UrlBasedCorsConfigurationSource src = new UrlBasedCorsConfigurationSource();
        src.registerCorsConfiguration("/**", c);
        return src;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
