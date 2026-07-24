package com.techindna.springbootjwttemplate.service;

import com.techindna.springbootjwttemplate.config.JwtTokenProvider;
import com.techindna.springbootjwttemplate.dto.MessageBody;
import com.techindna.springbootjwttemplate.dto.RegisterInput;
import com.techindna.springbootjwttemplate.dto.VerifyRegistrationResponse;
import com.techindna.springbootjwttemplate.entity.User;
import com.techindna.springbootjwttemplate.entity.email.EmailDetails;
import com.techindna.springbootjwttemplate.exception.http.ConflictException;
import com.techindna.springbootjwttemplate.exception.http.GoneException;
import com.techindna.springbootjwttemplate.exception.http.UnauthorizedException;
import com.techindna.springbootjwttemplate.exception.http.UnprocessableContentException;
import com.techindna.springbootjwttemplate.mapper.AuthMapper;
import com.techindna.springbootjwttemplate.repository.AuthRepository;
import com.techindna.springbootjwttemplate.repository.model.JUser;
import com.techindna.springbootjwttemplate.service.mail.EmailService;
import com.techindna.springbootjwttemplate.validator.DataValidator;
import com.techindna.springbootjwttemplate.validator.UserValidator;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Map;

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
    private final DataValidator dataValidator;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final VerificationCodeStore verificationCodeStore;
    private final JwtTokenProvider jwtTokenProvider;

    @Transactional
    public MessageBody register(RegisterInput request) {
        userValidator.validateRegistration(request);
        String encodedPassword = passwordEncoder.encode(request.getPassword());
        String code = generateCode();
        String email = request.getEmail().strip().toLowerCase();

        try {
            authRepository.save(authMapper.toEntity(request, encodedPassword));
            authRepository.flush();
            verificationCodeStore.save(email, code);
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

        emailService.sendMail(new EmailDetails(
                email,
                "Email Verification",
                "mail/verification",
                Map.of(
                        "code", code,
                        "firstName", request.getFirstName().strip(),
                        "lastName", request.getLastName().strip(),
                        "username", request.getUsername().strip(),
                        "email", email
                )));

        return new MessageBody("An email has been sent to verify your account");
    }

    @Transactional
    public VerifyRegistrationResponse verify(String code, String email) {
        dataValidator.validateCode(code);
        dataValidator.validateEmail("email", email);

        String normalizedEmail = email.strip().toLowerCase();

        JUser jUser = authRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new UnauthorizedException("Invalid verification code"));

        String storedCode = verificationCodeStore.getCodeByEmail(normalizedEmail)
                .orElseThrow(() -> new GoneException("Verification code has expired"));

        if (!MessageDigest.isEqual(storedCode.getBytes(), code.getBytes())) {
            throw new UnauthorizedException("Invalid verification code");
        }

        jUser.setVerified(true);
        authRepository.save(jUser);
        verificationCodeStore.delete(normalizedEmail);

        String token = jwtTokenProvider.generateToken(jUser.getId().toString(), jUser.getRole().name());
        User user = authMapper.toDomain(jUser);

        return new VerifyRegistrationResponse(token, user);
    }

    private static String generateCode() {
        StringBuilder sb = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            sb.append(CODE_CHARS.charAt(SECURE_RANDOM.nextInt(CODE_CHARS.length())));
        }
        return sb.toString();
    }
}
