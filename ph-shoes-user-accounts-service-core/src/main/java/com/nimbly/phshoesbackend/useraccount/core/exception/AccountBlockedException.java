package com.nimbly.phshoesbackend.useraccount.core.exception;

public class AccountBlockedException extends RuntimeException {
    public AccountBlockedException() { super(); }
    public AccountBlockedException(String msg) { super(msg); }
}