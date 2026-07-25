package com.techindna.springbootjwttemplate.service;

import com.techindna.springbootjwttemplate.security.jwt.JwtTokenProvider;
import com.techindna.springbootjwttemplate.dto.LoginInput;
import com.techindna.springbootjwttemplate.dto.MessageBody;
import com.techindna.springbootjwttemplate.dto.RegisterInput;
import com.techindna.springbootjwttemplate.dto.VerifyRegistrationResponse;
import com.techindna.springbootjwttemplate.entity.GeoIpResponse;
import com.techindna.springbootjwttemplate.entity.User;
import com.techindna.springbootjwttemplate.entity.email.EmailDetails;
import com.techindna.springbootjwttemplate.exception.http.ConflictException;
import com.techindna.springbootjwttemplate.exception.http.ForbiddenException;
import com.techindna.springbootjwttemplate.exception.http.UnauthorizedException;
import com.techindna.springbootjwttemplate.mapper.AuthMapper;
import com.techindna.springbootjwttemplate.repository.AuthRepository;
import com.techindna.springbootjwttemplate.repository.model.JUser;
import com.techindna.springbootjwttemplate.service.mail.EmailService;
import com.techindna.springbootjwttemplate.validator.UserValidator;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private static final String CODE_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final int CODE_LENGTH = 6;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final DateTimeFormatter REGISTERED_AT_FORMAT =
            DateTimeFormatter.ofPattern("MMM d, yyyy HH:mm 'UTC'").withZone(ZoneOffset.UTC);

    private final AuthRepository authRepository;
    private final AuthMapper authMapper;
    private final UserValidator userValidator;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final VerificationCodeStore verificationCodeStore;
    private final JwtTokenProvider jwtTokenProvider;
    private final GeoIpService geoIpService;

    @Value("${app.base-url}")
    private String baseUrl;

    @Transactional
    public MessageBody register(RegisterInput request, HttpServletRequest servletRequest) {
        userValidator.validateRegistration(request);
        String encodedPassword = passwordEncoder.encode(request.getPassword());
        String email = request.getEmail().strip().toLowerCase();
        String token = UUID.randomUUID().toString();

        try {
            authRepository.save(authMapper.toEntity(request, encodedPassword));
            authRepository.flush();
            verificationCodeStore.saveToken(email, token);
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

        String verificationUrl = String.format("%s/auth/verification/%s", baseUrl, token);

        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("verificationUrl", verificationUrl);
        variables.put("firstName", request.getFirstName().strip());
        variables.put("lastName", request.getLastName().strip());
        variables.put("username", request.getUsername().strip());
        variables.put("email", email);
        addClientData(variables, servletRequest);

        emailService.sendMail(new EmailDetails(
                email,
                "Email Verification",
                "mail/verification",
                variables));

        return new MessageBody("An email has been sent to verify your account");
    }

    @Transactional(readOnly = true)
    public MessageBody login(LoginInput request, HttpServletRequest servletRequest) {
        userValidator.validateLogin(request);

        JUser jUser = request.getEmail() != null && !request.getEmail().isBlank() ?
                authRepository.findByEmail(request.getEmail().strip().toLowerCase())
                        .orElseThrow(() -> new UnauthorizedException("Invalid credentials")) :
                authRepository.findByUsername(request.getUsername().strip())
                        .orElseThrow(() -> new UnauthorizedException("Invalid credentials"));

        if (!passwordEncoder.matches(request.getPassword(), jUser.getPassword())) {
            throw new UnauthorizedException("Invalid credentials");
        }

        if (!jUser.getVerified()) {
            throw new ForbiddenException("Account has not been verified");
        }

        String email = jUser.getEmail();
        String code = generateCode();
        verificationCodeStore.save(email, code);

        String verificationUrl = buildVerificationUrl(code, email);

        String clientIp = geoIpService.extractClientIp(servletRequest);
        String userAgent = servletRequest.getHeader("User-Agent");

        emailService.sendMail(new EmailDetails(
                email,
                "Login Verification",
                "mail/login-verification",
                Map.of(
                        "verificationUrl", verificationUrl,
                        "code", code,
                        "clientIp", clientIp,
                        "userAgent", userAgent != null ? userAgent : "Unknown"
                )));

        return new MessageBody("A verification code has been sent to your email");
    }

    @Transactional
    public VerifyRegistrationResponse verify(UUID token) {
        String tokenStr = token.toString();
        String email = verificationCodeStore.getEmailByToken(tokenStr)
                .orElseThrow(() -> new UnauthorizedException("Invalid or expired token"));

        JUser jUser = authRepository.findByEmail(email)
                .orElseThrow(() -> new UnauthorizedException("Invalid or expired token"));

        jUser.setVerified(true);
        authRepository.save(jUser);
        verificationCodeStore.deleteByToken(tokenStr);

        String jwtToken = jwtTokenProvider.generateToken(jUser.getId().toString(), jUser.getRole().name());
        User user = authMapper.toDomain(jUser);

        return new VerifyRegistrationResponse(jwtToken, user);
    }

    private static String generateCode() {
        StringBuilder sb = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            sb.append(CODE_CHARS.charAt(SECURE_RANDOM.nextInt(CODE_CHARS.length())));
        }
        return sb.toString();
    }

    private String buildVerificationUrl(String code, String email) {
        return String.format("%s/auth/verification?code=%s&email=%s",
                baseUrl,
                URLEncoder.encode(code, StandardCharsets.UTF_8),
                URLEncoder.encode(email, StandardCharsets.UTF_8));
    }

    private GeoIpResponse resolveGeoData(HttpServletRequest request) {
        String ip = geoIpService.extractClientIp(request);
        try {
            return geoIpService.lookup(ip);
        } catch (RuntimeException e) {
            log.warn("GeoIP lookup failed for IP {}: {}", ip, e.getMessage());
            return null;
        }
    }

    private void addClientData(Map<String, Object> variables, HttpServletRequest request) {
        String clientIp = geoIpService.extractClientIp(request);
        String userAgent = request.getHeader("User-Agent");

        variables.put("clientIp", clientIp);
        variables.put("userAgent", userAgent != null ? userAgent : "Unknown");
        variables.put("time", REGISTERED_AT_FORMAT.format(Instant.now()));

        GeoIpResponse geo = resolveGeoData(request);
        variables.put("city", geo != null && geo.city() != null ? geo.city() : "N/A");
        variables.put("country", geo != null && geo.country() != null ? geo.country() : "N/A");
        variables.put("countryCode", geo != null && geo.countryCode() != null ? geo.countryCode() : "N/A");
        variables.put("timezone", geo != null && geo.timezone() != null ? geo.timezone() : "N/A");
        variables.put("latitude", geo != null && geo.latitude() != null ? String.format("%.6f", geo.latitude()) : "N/A");
        variables.put("longitude", geo != null && geo.longitude() != null ? String.format("%.6f", geo.longitude()) : "N/A");
    }
}
