package com.techindna.springbootjwttemplate.entity.email;

import java.util.Map;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class EmailDetails {

    private String recipient;
    private String subject;
    private String template;
    private Map<String, Object> variables;
    private String attachment;

    public EmailDetails(String recipient, String subject, String template, Map<String, Object> variables) {
        this.recipient = recipient;
        this.subject = subject;
        this.template = template;
        this.variables = variables;
    }
}
