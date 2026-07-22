package com.techindna.springbootjwttemplate.exception.http;

public class ForbiddenException extends RuntimeException {

    public ForbiddenException(String message) {
        super(message);
    }
}
