package com.techindna.springbootjwttemplate.service.auth;

import com.techindna.springbootjwttemplate.dto.MessageBody;
import com.techindna.springbootjwttemplate.dto.RegisterInput;
import com.techindna.springbootjwttemplate.exception.http.ConflictException;
import com.techindna.springbootjwttemplate.mapper.UserMapper;
import com.techindna.springbootjwttemplate.repository.AuthRepository;
import com.techindna.springbootjwttemplate.service.mail.AuthMailService;
import com.techindna.springbootjwttemplate.validator.AuthValidator;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class RegisterService {

    private final AuthRepository authRepository;
    private final UserMapper userMapper;
    private final AuthValidator authValidator;
    private final PasswordEncoder passwordEncoder;
    private final AuthMailService authMailService;

    @Transactional
    public MessageBody register(RegisterInput request, HttpServletRequest servletRequest) {
        authValidator.validateRegistration(request);
        String encodedPassword = passwordEncoder.encode(request.getPassword());
        String email = request.getEmail().strip().toLowerCase();

        try {
            authRepository.saveAndFlush(userMapper.toEntity(request, encodedPassword));
        } catch (DataIntegrityViolationException e) {
            String constraint = e.getMostSpecificCause().getMessage();
            if (constraint != null && constraint.contains("email")) {
                throw new ConflictException("You cannot use this email address");
            }
            if (constraint != null && constraint.contains("username")) {
                throw new ConflictException("You cannot use this username");
            }
            throw e;
        }

        String token = authMailService.generateVerificationToken(email);

        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("verificationUrl", authMailService.verificationUrl(token));
        variables.put("firstName", request.getFirstName().strip());
        variables.put("lastName", request.getLastName().strip());
        variables.put("username", request.getUsername().strip());
        variables.put("email", email);

        authMailService.sendTemplatedEmail(email, "Email Verification", "mail/verification", variables, servletRequest);

        return new MessageBody("An email has been sent to verify your account");
    }
}