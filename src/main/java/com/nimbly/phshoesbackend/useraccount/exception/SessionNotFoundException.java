package com.nimbly.phshoesbackend.useraccount.exception;

public class SessionNotFoundException extends RuntimeException {
    public SessionNotFoundException() { super(); }
    public SessionNotFoundException(String msg) { super(msg); }
}