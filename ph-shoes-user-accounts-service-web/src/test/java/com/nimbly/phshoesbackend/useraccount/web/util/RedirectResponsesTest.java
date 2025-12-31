package com.nimbly.phshoesbackend.useraccount.web.util;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RedirectResponsesTest {

    @Test
    void seeOther_withError_appendsErrorQuery() {
        // Arrange
        String baseUrl = "https://frontend.example";
        String path = "/verify";

        // Act
        ResponseEntity<Void> response = RedirectResponses.seeOther(baseUrl, path, "missing_token");

        // Assert
        assertEquals(HttpStatus.SEE_OTHER, response.getStatusCode());
        assertEquals(URI.create("https://frontend.example/verify?error=missing_token"),
                response.getHeaders().getLocation());
    }

    @Test
    void seeOther_withoutError_returnsBasePath() {
        // Arrange
        String baseUrl = "https://frontend.example";
        String path = "/verify";

        // Act
        ResponseEntity<Void> response = RedirectResponses.seeOther(baseUrl, path, null);

        // Assert
        assertEquals(HttpStatus.SEE_OTHER, response.getStatusCode());
        assertEquals(URI.create("https://frontend.example/verify"),
                response.getHeaders().getLocation());
    }

    @Test
    void seeOther_withFlags_addsQueryParameters() {
        // Arrange
        String baseUrl = "https://frontend.example";
        String path = "/verify";

        // Act
        ResponseEntity<Void> response = RedirectResponses.seeOther(
                baseUrl,
                path,
                "send_failed",
                true,
                false,
                true
        );

        // Assert
        assertEquals(HttpStatus.SEE_OTHER, response.getStatusCode());
        assertEquals(URI.create("https://frontend.example/verify?error=send_failed&verified=true&resent=false&notMe=true"),
                response.getHeaders().getLocation());
    }

    @Test
    void seeOther_withFlags_skipsNulls() {
        // Arrange
        String baseUrl = "https://frontend.example";
        String path = "/verify";

        // Act
        ResponseEntity<Void> response = RedirectResponses.seeOther(
                baseUrl,
                path,
                null,
                null,
                null,
                null
        );

        // Assert
        assertEquals(HttpStatus.SEE_OTHER, response.getStatusCode());
        assertEquals(URI.create("https://frontend.example/verify"),
                response.getHeaders().getLocation());
    }
}
