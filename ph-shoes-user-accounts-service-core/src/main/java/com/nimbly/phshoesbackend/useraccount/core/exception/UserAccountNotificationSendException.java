package com.nimbly.phshoesbackend.useraccount.core.exception;

public class UserAccountNotificationSendException extends RuntimeException {
    public UserAccountNotificationSendException(String message, Throwable cause) {
        super(message, cause);
    }

    public UserAccountNotificationSendException(String message) {
        super(message);
    }
}
