package com.techindna.springbootjwttemplate.exception.http;

public class GoneException extends RuntimeException {

    public GoneException(String message) {
        super(message);
    }
}
