package com.techindna.springbootjwttemplate.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class LoginInput {
    private String username;
    private String email;
    private String password;

    public LoginInput(String username, String email, String password) {
        this.username = username;
        this.email = email;
        this.password = password;
    }
}
