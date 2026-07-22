package com.techindna.springbootjwttemplate.exception.http;

public class UnauthorizedException extends RuntimeException {

    public UnauthorizedException(String message) {
        super(message);
    }
}
