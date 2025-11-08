package com.nimbly.phshoesbackend.useraccount.subscribe;

/**
 * Handles resubscription workflows using signed unsubscribe tokens.
 */
public interface SubscribeService {

    /**
     * Removes the underlying email hash from the suppression list.
     *
     * @param token signed token produced by {@link com.nimbly.phshoesbackend.useraccount.unsubscribe.UnsubscribeTokenCodec}
     */
    void resubscribe(String token);
}
