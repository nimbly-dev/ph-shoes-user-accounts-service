package com.nimbly.phshoesbackend.useraccount.core.exception;

public class VerificationExpiredException extends RuntimeException{
    public VerificationExpiredException(String msg) { super(msg); }
}
