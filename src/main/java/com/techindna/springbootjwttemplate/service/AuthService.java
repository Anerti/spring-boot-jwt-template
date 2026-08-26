package com.techindna.springbootjwttemplate.service;

import com.techindna.springbootjwttemplate.entity.enums.HostStatus;
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
import com.techindna.springbootjwttemplate.mapper.UserMapper;
import com.techindna.springbootjwttemplate.repository.AuthRepository;
import com.techindna.springbootjwttemplate.repository.HostRepository;
import com.techindna.springbootjwttemplate.repository.model.JHost;
import com.techindna.springbootjwttemplate.repository.model.JUser;
import com.techindna.springbootjwttemplate.service.mail.EmailService;
import com.techindna.springbootjwttemplate.service.redis.FailedLoginTracker;
import com.techindna.springbootjwttemplate.service.redis.VerificationCodeStore;
import com.techindna.springbootjwttemplate.validator.DataValidator;
import com.techindna.springbootjwttemplate.validator.UserValidator;
import com.techindna.springbootjwttemplate.entity.enums.UserStatus;
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
    private static final DateTimeFormatter REGISTERED_AT_FORMAT =
            DateTimeFormatter.ofPattern("MMM d, yyyy HH:mm 'UTC'").withZone(ZoneOffset.UTC);
    private static final int MAX_FAILED_LOGIN_ATTEMPTS = 5;

    private final AuthRepository authRepository;
    private final UserMapper userMapper;
    private final UserValidator userValidator;
    private final DataValidator dataValidator;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final VerificationCodeStore verificationCodeStore;
    private final JwtTokenProvider jwtTokenProvider;
    private final GeoIpService geoIpService;
    private final HostRepository hostRepository;
    private final FailedLoginTracker failedLoginTracker;

    @Value("${app.base-url}")
    private String baseUrl;

    @Transactional
    public MessageBody register(RegisterInput request, HttpServletRequest servletRequest) {
        userValidator.validateRegistration(request);
        String encodedPassword = passwordEncoder.encode(request.getPassword());
        String email = request.getEmail().strip().toLowerCase();

        try {
            authRepository.save(userMapper.toEntity(request, encodedPassword));
            authRepository.flush();
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

        sendVerificationLink(email, request.getFirstName().strip(), request.getLastName().strip(),
                request.getUsername().strip(), "Email Verification", "mail/verification", servletRequest);

        return new MessageBody("An email has been sent to verify your account");
    }

    @Transactional(noRollbackFor = {ForbiddenException.class})
    public MessageBody login(LoginInput request, HttpServletRequest servletRequest) {
        userValidator.validateLogin(request);

        JUser jUser = request.getEmail() != null && !request.getEmail().isBlank() ?
                authRepository.findByEmail(request.getEmail().strip().toLowerCase())
                        .orElseThrow(() -> new UnauthorizedException("Invalid credentials")) :
                authRepository.findByUsername(request.getUsername().strip())
                        .orElseThrow(() -> new UnauthorizedException("Invalid credentials"));

        if (UserStatus.LOCKED == jUser.getStatus()) {
            recordHostAndCheckBan(jUser.getId(), servletRequest, "Login attempt on locked account");
            throw new ForbiddenException("Account locked");
        }

        if (!jUser.getVerified()) {
            throw new ForbiddenException("Account has not been verified");
        }

        if (passwordEncoder.matches(request.getPassword(), jUser.getPassword())) {
            recordHostAndCheckBan(jUser.getId(), servletRequest, "Login successful");
            sendVerificationLink(jUser.getEmail(), jUser.getFirstName(), jUser.getLastName(),
                    jUser.getUsername(), "Login Verification", "mail/login-verification", servletRequest);
            return new MessageBody("A verification link has been sent to your email");
        }

        int failedCount = failedLoginTracker.increment(jUser.getId());
        recordHostAndCheckBan(jUser.getId(), servletRequest, "Login failed: invalid credentials");

        if (failedCount == MAX_FAILED_LOGIN_ATTEMPTS) {
            jUser.setStatus(UserStatus.LOCKED);
            authRepository.save(jUser);
            throw new ForbiddenException("Account has been locked due to multiple failed logins");
        }

        throw new UnauthorizedException(String.format("Invalid credentials. %s attempt(s) left", MAX_FAILED_LOGIN_ATTEMPTS - failedCount));
    }

    private void recordHostAndCheckBan(UUID userId, HttpServletRequest servletRequest, String description) {
        String ipAddress = geoIpService.extractClientIp(servletRequest);
        String rawUserAgent = servletRequest.getHeader("User-Agent");

        JHost host = hostRepository.findByIpAddressAndUserId(ipAddress, userId)
                .orElseGet(() -> JHost.builder()
                        .userId(userId)
                        .ipAddress(ipAddress)
                        .userAgent((rawUserAgent != null && !rawUserAgent.isBlank()) ? rawUserAgent : "Unknown")
                        .description(description)
                        .build());

        if (host.getStatus() == HostStatus.BANNED){
            throw new ForbiddenException(String.format("Host %s is banned from accessing this account", ipAddress));
        }

        hostRepository.save(host);
    }

    @Transactional
    public VerifyRegistrationResponse verify(UUID token, HttpServletRequest servletRequest) {
        String tokenStr = token.toString();
        String email = verificationCodeStore.getEmailByToken(tokenStr)
                .orElseThrow(() -> new UnauthorizedException("Invalid or expired token"));

        JUser jUser = authRepository.findByEmail(email)
                .orElseThrow(() -> new UnauthorizedException("Invalid or expired token"));

        if (!jUser.getVerified()) {
            jUser.setVerified(true);
            authRepository.save(jUser);
        }

        verificationCodeStore.deleteByToken(tokenStr);

        String clientIp = geoIpService.extractClientIp(servletRequest);
        String jwtToken = jwtTokenProvider.generateToken(jUser.getId().toString(), jUser.getRole().name(), clientIp);
        User user = userMapper.toDomain(jUser);

        return new VerifyRegistrationResponse(jwtToken, user);
    }

    @Transactional
    public MessageBody resendVerificationLink(String email, HttpServletRequest servletRequest) {
        dataValidator.validateEmail("email", email);

        String normalizedEmail = email.strip().toLowerCase();
        JUser jUser = authRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new ForbiddenException("No pending verification"));

        if (jUser.getVerified()) {
            throw new ForbiddenException("No pending verification");
        }

        sendVerificationLink(normalizedEmail, jUser.getFirstName(), jUser.getLastName(),
                jUser.getUsername(), "Email Verification", "mail/verification", servletRequest);

        return new MessageBody("A verification link has been sent to your email");
    }

    private void sendVerificationLink(String email, String firstName, String lastName,
            String username, String subject, String template, HttpServletRequest servletRequest) {
        String token = UUID.randomUUID().toString();
        verificationCodeStore.saveToken(email, token);

        String verificationUrl = String.format("%s/auth/verification/%s", baseUrl, token);

        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("verificationUrl", verificationUrl);
        variables.put("firstName", firstName);
        variables.put("lastName", lastName);
        variables.put("username", username);
        variables.put("email", email);
        addClientData(variables, servletRequest);

        emailService.sendMail(new EmailDetails(email, subject, template, variables));
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
