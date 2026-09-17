package com.wallet.wallet.exception;

public class DuplicateTransactionException extends RuntimeException{
    public DuplicateTransactionException (String message) {
        super(message);
    }
}
