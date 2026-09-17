package com.wallet.wallet.exception;

public class InactiveAccountException extends RuntimeException{
    public InactiveAccountException(String message) {
        super(message);
    }
}
