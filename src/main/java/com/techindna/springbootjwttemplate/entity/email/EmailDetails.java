package com.techindna.springbootjwttemplate.entity.email;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class EmailDetails {

    private String recipient;
    private String msgBody;
    private String subject;
    private String attachment;

    public EmailDetails(String recipient, String msgBody, String subject) {
        this(recipient, msgBody, subject, null);
    }
}
