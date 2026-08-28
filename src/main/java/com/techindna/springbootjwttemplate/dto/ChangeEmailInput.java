package com.techindna.springbootjwttemplate.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class ChangeEmailInput {
    private String newEmail;
    private String password;
}
