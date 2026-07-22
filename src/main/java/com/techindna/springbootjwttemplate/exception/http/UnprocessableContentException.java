package com.techindna.springbootjwttemplate.exception.http;

public class UnprocessableContentException extends RuntimeException {

    public UnprocessableContentException(String message) {
        super(message);
    }
}
