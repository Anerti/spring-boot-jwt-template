package com.techindna.springbootjwttemplate.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class ChangePasswordInput {
    private String oldPassword;
    private String newPassword;
    private String confirmNewPassword;
}
