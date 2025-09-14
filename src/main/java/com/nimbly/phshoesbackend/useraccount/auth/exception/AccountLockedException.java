package com.nimbly.phshoesbackend.useraccount.auth.exception;

public class AccountLockedException extends RuntimeException {
    public AccountLockedException() {
        super("Account temporarily locked. Try again later.");
    }
}
