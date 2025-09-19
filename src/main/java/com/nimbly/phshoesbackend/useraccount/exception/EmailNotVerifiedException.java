package com.nimbly.phshoesbackend.useraccount.exception;

public class EmailNotVerifiedException extends RuntimeException {
    public EmailNotVerifiedException() { super(); }
    public EmailNotVerifiedException(String message) { super(message); }
}
