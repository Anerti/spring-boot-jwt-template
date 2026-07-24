package com.techindna.springbootjwttemplate.validator;

import com.techindna.springbootjwttemplate.dto.LoginInput;
import com.techindna.springbootjwttemplate.dto.RegisterInput;
import com.techindna.springbootjwttemplate.exception.http.UnprocessableContentException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class UserValidator {

    private final DataValidator dataValidator;

    public void validateLogin(LoginInput request) {
        dataValidator.checkNullData("password", request.getPassword());

        if ((request.getUsername() == null || request.getUsername().isBlank())
                && (request.getEmail() == null || request.getEmail().isBlank())) {
            throw new UnprocessableContentException("Username or email is required and cannot be blank");
        }
    }

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
