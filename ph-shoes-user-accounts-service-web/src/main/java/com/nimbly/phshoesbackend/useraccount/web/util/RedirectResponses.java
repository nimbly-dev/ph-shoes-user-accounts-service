package com.nimbly.phshoesbackend.useraccount.web.util;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;

public class RedirectResponses {

    public static ResponseEntity<Void> seeOther(
            String base,
            String path,
            String errorCode
    ) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(base).path(path);
        if (errorCode != null) {
            builder.queryParam("error", errorCode);
        }
        URI location = builder.build(true).toUri();
        return ResponseEntity.status(HttpStatus.SEE_OTHER).location(location).build();
    }

    public static ResponseEntity<Void> seeOther(
            String base,
            String path,
            String errorCode,
            Boolean verified,
            Boolean resent,
            Boolean notMe
    ) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(base).path(path);
        if (errorCode != null) {
            builder.queryParam("error", errorCode);
        }
        if (verified != null) {
            builder.queryParam("verified", verified);
        }
        if (resent != null) {
            builder.queryParam("resent", resent);
        }
        if (notMe != null) {
            builder.queryParam("notMe", notMe);
        }
        URI location = builder.build(true).toUri();
        return ResponseEntity.status(HttpStatus.SEE_OTHER).location(location).build();
    }
}
