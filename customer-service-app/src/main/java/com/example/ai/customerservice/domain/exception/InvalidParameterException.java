package com.example.ai.customerservice.domain.exception;

public class InvalidParameterException extends RuntimeException {

    public InvalidParameterException(String message) {
        super(message);
    }
}
