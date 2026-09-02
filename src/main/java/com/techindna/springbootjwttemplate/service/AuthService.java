package com.techindna.springbootjwttemplate.service;

import com.techindna.springbootjwttemplate.dto.MessageBody;
import com.techindna.springbootjwttemplate.exception.http.ForbiddenException;
import com.techindna.springbootjwttemplate.repository.AuthRepository;
import com.techindna.springbootjwttemplate.repository.model.JUser;
import com.techindna.springbootjwttemplate.service.mail.AuthMailService;
import com.techindna.springbootjwttemplate.validator.DataValidator;

import java.util.*;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthRepository authRepository;
    private final DataValidator dataValidator;
    private final AuthMailService authMailService;

    @Transactional
    public MessageBody resendVerificationLink(String email, HttpServletRequest servletRequest) {
        dataValidator.validateEmail("email", email);

        String normalizedEmail = email.strip().toLowerCase();
        JUser jUser = authRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new ForbiddenException("No pending verification"));

        if (jUser.getVerified()) {
            throw new ForbiddenException("No pending verification");
        }

        String token = authMailService.generateVerificationToken(normalizedEmail);

        Map<String, Object> variables = new HashMap<>();
        variables.put("verificationUrl", authMailService.verificationUrl(token));
        variables.put("firstName", jUser.getFirstName());
        variables.put("lastName", jUser.getLastName());
        variables.put("username", jUser.getUsername());
        variables.put("email", normalizedEmail);

        authMailService.sendTemplatedEmail(normalizedEmail, "Email Verification", "mail/verification", variables, servletRequest);

        return new MessageBody("A verification link has been sent to your email");
    }
}
