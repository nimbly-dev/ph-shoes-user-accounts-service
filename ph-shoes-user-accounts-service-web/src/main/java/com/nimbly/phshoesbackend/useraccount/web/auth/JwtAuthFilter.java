package com.nimbly.phshoesbackend.useraccount.web.auth;

import com.nimbly.phshoesbackend.useraccount.core.auth.JwtTokenProvider;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

/**
 * Populates Spring SecurityContext from a valid Bearer JWT using JwtTokenProvider.
 * Requires SecurityConfig to add: .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
 */
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;

    public JwtAuthFilter(JwtTokenProvider jwtTokenProvider) {
        this.jwtTokenProvider = jwtTokenProvider;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {

        String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);

        if (StringUtils.hasText(authHeader) && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);

            try {
                var decoded = jwtTokenProvider.parseAccess(token); // validates signature/issuer/exp
                String userId = decoded.getSubject();              // sub
                String email  = decoded.getClaim("email").asString();

                // Principal = userId; store email in details (or swap if you prefer email as principal)
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(
                                userId,
                                null,
                                Collections.emptyList() // add authorities here if you have roles in the token
                        );

                // Attach request details + a lightweight JWT details object
                authentication.setDetails(new JwtDetails(userId, email,
                        new WebAuthenticationDetailsSource().buildDetails(request)));

                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (Exception ex) {
                // Invalid/expired token → ensure context is clear and continue the chain
                SecurityContextHolder.clearContext();
            }
        }

        chain.doFilter(request, response);
    }

    /** Optional details holder you can access from controllers via ((JwtDetails) auth.getDetails()) */
    public static final class JwtDetails {
        private final String userId;
        private final String email;
        private final Object requestDetails;

        public JwtDetails(String userId, String email, Object requestDetails) {
            this.userId = userId;
            this.email = email;
            this.requestDetails = requestDetails;
        }
        public String getUserId() { return userId; }
        public String getEmail()  { return email; }
        public Object getRequestDetails() { return requestDetails; }
    }
}
