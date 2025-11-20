package com.nimbly.phshoesbackend.useraccount.core.exception;

public class InvalidVerificationTokenException extends RuntimeException{
    public InvalidVerificationTokenException(String msg) { super(msg); }
}
