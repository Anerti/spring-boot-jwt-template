package com.techindna.springbootjwttemplate.exception.http;

public class MailSendFailureException extends RuntimeException {

    public MailSendFailureException(String message) {
        super(message);
    }
}
