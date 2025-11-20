package com.nimbly.phshoesbackend.useraccount.core.exception;

public class NotificationSendException extends RuntimeException {
    public NotificationSendException(String message, Throwable cause) {
        super(message, cause);
    }

    public NotificationSendException(String message) {
        super(message);
    }
}
