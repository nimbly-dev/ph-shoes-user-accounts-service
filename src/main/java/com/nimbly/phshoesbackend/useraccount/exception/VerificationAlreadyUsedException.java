package com.nimbly.phshoesbackend.useraccount.exception;

public class VerificationAlreadyUsedException extends RuntimeException{
    public VerificationAlreadyUsedException(String msg) { super(msg); }
}
