package com.techindna.springbootjwttemplate.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class UpdateUserInput {
    private String username;
    private String firstName;
    private String lastName;
}
