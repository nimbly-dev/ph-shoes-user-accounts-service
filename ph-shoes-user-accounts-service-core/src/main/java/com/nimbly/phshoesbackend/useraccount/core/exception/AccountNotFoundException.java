package com.nimbly.phshoesbackend.useraccount.core.exception;

public class AccountNotFoundException extends RuntimeException {
    public AccountNotFoundException() { super(); }
    public AccountNotFoundException(String msg) { super(msg); }
}