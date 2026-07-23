package com.techindna.springbootjwttemplate.service.mail;

import com.techindna.springbootjwttemplate.entity.email.EmailDetails;

public interface EmailService {
    void sendMail(EmailDetails details);
    void sendMailWithAttachment(EmailDetails details);
}
