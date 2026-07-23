package com.techindna.springbootjwttemplate.service;

import com.techindna.springbootjwttemplate.dto.MessageBody;
import com.techindna.springbootjwttemplate.dto.RegisterInput;
import com.techindna.springbootjwttemplate.entity.email.EmailDetails;
import com.techindna.springbootjwttemplate.exception.http.ConflictException;
import com.techindna.springbootjwttemplate.mapper.AuthMapper;
import com.techindna.springbootjwttemplate.repository.AuthRepository;
import com.techindna.springbootjwttemplate.service.mail.EmailService;
import com.techindna.springbootjwttemplate.validator.UserValidator;
import java.security.SecureRandom;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private static final String CODE_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final int CODE_LENGTH = 6;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final AuthRepository authRepository;
    private final AuthMapper authMapper;
    private final UserValidator userValidator;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;

    @Transactional
    public MessageBody register(RegisterInput request) {
        userValidator.validateRegistration(request);
        String encodedPassword = passwordEncoder.encode(request.getPassword());
        String code = generateCode();
        String email = request.getEmail().strip().toLowerCase();

        try {
            authRepository.save(authMapper.toEntity(request, encodedPassword, code));
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException("A user with that email or username already exists");
        }

        emailService.sendMail(new EmailDetails(
                email, "Your verification code is: " + code, "Email Verification"));

        return new MessageBody("A verification code has been sent to your email");
    }

    private static String generateCode() {
        StringBuilder sb = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            sb.append(CODE_CHARS.charAt(SECURE_RANDOM.nextInt(CODE_CHARS.length())));
        }
        return sb.toString();
    }
}
