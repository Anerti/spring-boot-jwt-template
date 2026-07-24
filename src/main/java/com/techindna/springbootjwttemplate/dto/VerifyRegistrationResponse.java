package com.techindna.springbootjwttemplate.dto;

import com.techindna.springbootjwttemplate.entity.User;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class VerifyRegistrationResponse {
    private String token;
    private User user;
}
