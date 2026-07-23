package com.techindna.springbootjwttemplate.validator;

import com.techindna.springbootjwttemplate.dto.RegisterInput;
import com.techindna.springbootjwttemplate.exception.http.UnprocessableContentException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class UserValidator {

    private final DataValidator dataValidator;

    public void validateRegistration(RegisterInput request) {

        dataValidator.validateEmail("email", request.getEmail());

        dataValidator.checkPasswordSecurityLevel(request.getPassword());

        dataValidator.checkNullData("confirmPassword", request.getConfirmPassword());
        if (!request.getPassword().equals(request.getConfirmPassword())) {
            throw new UnprocessableContentException("Passwords do not match");
        }

        dataValidator.validateName("firstName", request.getFirstName());
        dataValidator.validateName("lastName", request.getLastName());

        dataValidator.validateUsername(request.getUsername());
    }
}
