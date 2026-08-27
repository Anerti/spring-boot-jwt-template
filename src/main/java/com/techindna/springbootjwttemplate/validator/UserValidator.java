package com.techindna.springbootjwttemplate.validator;

import com.techindna.springbootjwttemplate.dto.UpdateUserInput;
import com.techindna.springbootjwttemplate.repository.model.JUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class UserValidator {

    private final DataValidator dataValidator;

    public void validateAndApplyUpdate(UpdateUserInput input, JUser jUser) {
        if (input.getUsername() != null && !input.getUsername().isBlank()) {
            dataValidator.validateUsername(input.getUsername());
            jUser.setUsername(input.getUsername().strip());
        }
        if (input.getFirstName() != null && !input.getFirstName().isBlank()) {
            dataValidator.validateName("firstName", input.getFirstName());
            jUser.setFirstName(input.getFirstName().strip());
        }
        if (input.getLastName() != null && !input.getLastName().isBlank()) {
            dataValidator.validateName("lastName", input.getLastName());
            jUser.setLastName(input.getLastName().strip());
        }
    }
}
