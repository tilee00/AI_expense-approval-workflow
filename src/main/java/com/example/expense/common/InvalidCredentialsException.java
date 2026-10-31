package com.example.expense.common;

public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException() {
        super("Bad credentials");
    }
}
