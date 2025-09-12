package com.nimbly.phshoesbackend.useraccount.exception;

public class VerificationExpiredException extends RuntimeException{
    public VerificationExpiredException(String msg) { super(msg); }
}
