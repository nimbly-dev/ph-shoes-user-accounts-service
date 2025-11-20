package com.nimbly.phshoesbackend.useraccount.core.exception;

public class VerificationAlreadyUsedException extends RuntimeException{
    public VerificationAlreadyUsedException(String msg) { super(msg); }
}
