package com.nimbly.phshoesbackend.useraccount.exception;

public class InvalidVerificationTokenException extends RuntimeException{
    public InvalidVerificationTokenException(String msg) { super(msg); }
}
