package com.nimbly.phshoesbackend.useraccount.unsubscribe;

import java.util.Optional;

/**
 * Encapsulates unsubscribe workflows and header generation for RFC 8058 support.
 */
public interface UnsubscribeService {

    /**
     * Suppresses future notifications for the email represented by the token.
     *
     * @param token signed unsubscribe token
     */
    void unsubscribe(String token);

    /**
     * Builds the List-Unsubscribe header value for an email hash, if possible.
     *
     * @param emailHash hashed email address
     * @return optional header value
     */
    Optional<String> buildListUnsubscribeHeader(String emailHash);
}
