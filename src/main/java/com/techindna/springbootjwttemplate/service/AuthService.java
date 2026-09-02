package com.techindna.springbootjwttemplate.service;

import com.techindna.springbootjwttemplate.entity.enums.EventLogStatus;
import com.techindna.springbootjwttemplate.dto.ChangeEmailInput;
import com.techindna.springbootjwttemplate.dto.ChangePasswordInput;
import com.techindna.springbootjwttemplate.dto.LoginInput;
import com.techindna.springbootjwttemplate.dto.MessageBody;
import com.techindna.springbootjwttemplate.dto.RegisterInput;
import com.techindna.springbootjwttemplate.dto.UnlockAccountInput;
import com.techindna.springbootjwttemplate.dto.VerifyRegistrationResponse;
import com.techindna.springbootjwttemplate.entity.User;
import com.techindna.springbootjwttemplate.entity.email.EmailDetails;
import com.techindna.springbootjwttemplate.exception.http.ConflictException;
import com.techindna.springbootjwttemplate.exception.http.ForbiddenException;
import com.techindna.springbootjwttemplate.exception.http.UnauthorizedException;
import com.techindna.springbootjwttemplate.exception.http.UnprocessableContentException;
import com.techindna.springbootjwttemplate.mapper.UserMapper;
import com.techindna.springbootjwttemplate.repository.AuthRepository;
import com.techindna.springbootjwttemplate.repository.model.JHost;
import com.techindna.springbootjwttemplate.repository.model.JUser;
import com.techindna.springbootjwttemplate.service.mail.AuthMailService;
import com.techindna.springbootjwttemplate.service.mail.EmailService;
import com.techindna.springbootjwttemplate.service.redis.FailedLoginTracker;
import com.techindna.springbootjwttemplate.service.redis.VerificationCodeStore;
import com.techindna.springbootjwttemplate.validator.DataValidator;
import com.techindna.springbootjwttemplate.validator.AuthValidator;
import com.techindna.springbootjwttemplate.entity.enums.UserStatus;
import com.techindna.springbootjwttemplate.security.jwt.JwtTokenProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.*;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private static final int MAX_FAILED_LOGIN_ATTEMPTS = 5;

    private final AuthRepository authRepository;
    private final ABACRulesService abacRulesService;
    private final UserMapper userMapper;
    private final AuthValidator authValidator;
    private final DataValidator dataValidator;
    private final PasswordEncoder passwordEncoder;
    private final AuthMailService authMailService;
    private final VerificationCodeStore verificationCodeStore;
    private final JwtTokenProvider jwtTokenProvider;
    private final GeoIpService geoIpService;
    private final HostEventService hostEventService;
    private final FailedLoginTracker failedLoginTracker;
    private final EmailService emailService;

    @Value("${app.base-url}")
    private String baseUrl;

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

        String token = UUID.randomUUID().toString();
        verificationCodeStore.saveToken(email, token);

        String verificationUrl = String.format("%s/auth/verification/%s", baseUrl, token);

        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("verificationUrl", verificationUrl);
        variables.put("firstName", request.getFirstName().strip());
        variables.put("lastName", request.getLastName().strip());
        variables.put("username", request.getUsername().strip());
        variables.put("email", email);

        authMailService.addClientData(variables, servletRequest);

        emailService.sendMail(new EmailDetails(email, "Email Verification", "mail/verification", variables));

        return new MessageBody("An email has been sent to verify your account");
    }

    @Transactional(noRollbackFor = {ForbiddenException.class, UnauthorizedException.class})
    public MessageBody login(LoginInput request, HttpServletRequest servletRequest) {
        authValidator.validateLogin(request);

        JUser jUser = request.getEmail() != null && !request.getEmail().isBlank() ?
                authRepository.findByEmail(request.getEmail().strip().toLowerCase())
                        .orElseThrow(() -> new UnauthorizedException("Invalid credentials")) :
                authRepository.findByUsername(request.getUsername().strip())
                        .orElseThrow(() -> new UnauthorizedException("Invalid credentials"));

        JHost host = hostEventService.recordHostAndCheckBan(jUser, servletRequest);

        if (UserStatus.LOCKED == jUser.getStatus()) {
            hostEventService.logHostEvent(host, "LOGIN_FAILED : account locked", EventLogStatus.SECURITY, servletRequest);
            throw new ForbiddenException("Account locked");
        }

        if (!jUser.getVerified()) {
            hostEventService.logHostEvent(host, "LOGIN_FAILED : unverified account", EventLogStatus.SECURITY, servletRequest);
            throw new ForbiddenException("Account has not been verified");
        }

        String token = UUID.randomUUID().toString();
        if (passwordEncoder.matches(request.getPassword(), jUser.getPassword())) {
            hostEventService.logHostEvent(host, "LOGIN_SUCCESS : verification link sent", EventLogStatus.APPROVED, servletRequest);
            verificationCodeStore.saveToken(jUser.getEmail(), token);
            String verificationUrl = String.format("%s/auth/verification/%s", baseUrl, token);

            Map<String, Object> variables = new HashMap<>();
            variables.put("firstName", jUser.getFirstName());
            variables.put("verificationUrl", verificationUrl);
            variables.put("email", jUser.getEmail());
            authMailService.addClientData(variables, servletRequest);

            emailService.sendMail(new EmailDetails(jUser.getEmail(), "Login Verification", "mail/login-verification", variables));
            return new MessageBody("A verification link has been sent to your email");
        }

        int failedCount = failedLoginTracker.increment(jUser.getId());
        hostEventService.logHostEvent(host, "LOGIN_FAILED : invalid credentials", EventLogStatus.SECURITY, servletRequest);

        if (failedCount == MAX_FAILED_LOGIN_ATTEMPTS) {
            jUser.setStatus(UserStatus.LOCKED);
            authRepository.save(jUser);
            hostEventService.logHostEvent(host, "ACCOUNT_LOCKED : repeated failed login attempts", EventLogStatus.SECURITY, servletRequest);

            Map<String, Object> variables = new HashMap<>();
            variables.put("firstName", jUser.getFirstName());
            authMailService.addClientData(variables, servletRequest);

            emailService.sendMail(new EmailDetails(jUser.getEmail(), "Security Alert: Your account has been locked", "mail/account-locked", variables));
            throw new ForbiddenException("Account has been locked due to multiple failed logins");
        }

        throw new UnauthorizedException(String.format("Invalid credentials. %s attempt(s) left", MAX_FAILED_LOGIN_ATTEMPTS - failedCount));
    }

    @Transactional(noRollbackFor = {UnprocessableContentException.class, ForbiddenException.class, UnauthorizedException.class})
    public MessageBody changePassword(ChangePasswordInput request, HttpServletRequest servletRequest, Authentication auth) {
        String userId = SecurityContextHolder.getContext().getAuthentication().getName();
        JUser jUser = authRepository.findById(UUID.fromString(userId))
                .orElseThrow(() -> new UnauthorizedException("Invalid credentials"));

        abacRulesService.enforceIpBinding(auth, servletRequest);
        JHost userHost = hostEventService.recordHostAndCheckBan(jUser, servletRequest);

        if (UserStatus.LOCKED == jUser.getStatus()) {
            hostEventService.logHostEvent(userHost, "PASSWORD_CHANGE_FAILED : account locked", EventLogStatus.SECURITY, servletRequest);
            throw new ForbiddenException("Account locked");
        }

        if (!jUser.getVerified()) {
            hostEventService.logHostEvent(userHost, "PASSWORD_CHANGE_FAILED : account not verified", EventLogStatus.SECURITY, servletRequest);
            throw new ForbiddenException("Account has not been verified");
        }

        authValidator.validateChangePassword(request);

        if (!passwordEncoder.matches(request.getOldPassword(), jUser.getPassword())) {
            hostEventService.logHostEvent(userHost, "PASSWORD_CHANGE_FAILED : wrong current password", EventLogStatus.SECURITY, servletRequest);
            throw new UnauthorizedException("Invalid credentials");
        }

        if (passwordEncoder.matches(request.getNewPassword(), jUser.getPassword())) {
            hostEventService.logHostEvent(userHost, "PASSWORD_CHANGE_FAILED : new password matches current", EventLogStatus.WARNING, servletRequest);
            throw new UnprocessableContentException("Cannot use the current password");
        }

        jUser.setPassword(passwordEncoder.encode(request.getNewPassword()));
        authRepository.save(jUser);
        hostEventService.logHostEvent(userHost, "PASSWORD_CHANGE_SUCCEEDED", EventLogStatus.APPROVED, servletRequest);

        Map<String, Object> variables = new HashMap<>();
        variables.put("firstName", jUser.getFirstName());
        authMailService.addClientData(variables, servletRequest);

        emailService.sendMail(new EmailDetails(jUser.getEmail(), "Password Changed", "mail/password-change", variables));

        return new MessageBody("Password changed successfully");
    }

    @Transactional(noRollbackFor = {UnprocessableContentException.class, ForbiddenException.class, UnauthorizedException.class})
    public MessageBody changeEmail(ChangeEmailInput request, HttpServletRequest servletRequest, Authentication auth) {
        String userId = Objects.requireNonNull(SecurityContextHolder.getContext().getAuthentication()).getName();
        JUser jUser = authRepository.findById(UUID.fromString(userId))
                .orElseThrow(() -> new UnauthorizedException("Invalid credentials"));

        abacRulesService.enforceIpBinding(auth, servletRequest);
        JHost userHost = hostEventService.recordHostAndCheckBan(jUser, servletRequest);

        if (UserStatus.LOCKED == jUser.getStatus()) {
            hostEventService.logHostEvent(userHost, "EMAIL_CHANGE_FAILED : account locked", EventLogStatus.SECURITY, servletRequest);
            throw new ForbiddenException("Account locked");
        }

        authValidator.validateChangeEmail(request);

        String normalizedNewEmail = request.getNewEmail().strip().toLowerCase();
        if (normalizedNewEmail.equals(jUser.getEmail())) {
            hostEventService.logHostEvent(userHost, "EMAIL_CHANGE_FAILED : new email matches current", EventLogStatus.WARNING, servletRequest);
            throw new UnprocessableContentException("The new email must be different from the current email");
        }

        if (authRepository.findByEmail(normalizedNewEmail).isPresent()) {
            throw new ConflictException("Cannot use this email");
        }

        if (passwordEncoder.matches(request.getPassword(), jUser.getPassword())) {
            String token = UUID.randomUUID().toString();
            verificationCodeStore.saveToken(jUser.getEmail(), token);
            verificationCodeStore.savePendingEmail(token, normalizedNewEmail);

            String verificationUrl = String.format("%s/auth/verification/%s", baseUrl, token);

            Map<String, Object> variables = new HashMap<>();
            variables.put("verificationUrl", verificationUrl);
            variables.put("firstName", jUser.getFirstName());
            authMailService.addClientData(variables, servletRequest);

            emailService.sendMail(new EmailDetails(normalizedNewEmail, "Confirm your new email address", "mail/change-email", variables));

            hostEventService.logHostEvent(userHost, "EMAIL_CHANGE_PENDING : confirmation sent to new address", EventLogStatus.INFO, servletRequest);
            return new MessageBody("A verification link has been sent to your new email address");
        }

        hostEventService.logHostEvent(userHost, "EMAIL_CHANGE_FAILED : wrong current password", EventLogStatus.SECURITY, servletRequest);
        throw new UnauthorizedException("Invalid credentials");
    }

    @Transactional
    public MessageBody unlockAccount(UnlockAccountInput request, HttpServletRequest servletRequest) {
        dataValidator.validateEmail("email", request.getEmail());
        String normalizedEmail = request.getEmail().strip().toLowerCase();
        JUser jUser = authRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new ForbiddenException("Account not locked"));

        JHost userHost = hostEventService.recordHostAndCheckBan(jUser, servletRequest);
        if (jUser.getStatus() != UserStatus.LOCKED) {
            hostEventService.logHostEvent(userHost, "UNLOCK_FAILED : account not locked", EventLogStatus.WARNING, servletRequest);
            throw new ForbiddenException("Account not locked");
        }

        authMailService.sendVerificationLink(jUser.getEmail(), jUser.getFirstName(), jUser.getLastName(),
                jUser.getUsername(), "Account Unlock", "mail/unlock-account", servletRequest);

        hostEventService.logHostEvent(userHost, "UNLOCK_REQUESTED : verification email sent", EventLogStatus.INFO, servletRequest);
        return new MessageBody("A verification link has been sent to your email to unlock your account");
    }

    @Transactional
    public VerifyRegistrationResponse verify(UUID token, HttpServletRequest servletRequest) {
        String tokenStr = token.toString();
        String email = verificationCodeStore.getEmailByToken(tokenStr)
                .orElseThrow(() -> new UnauthorizedException("Invalid or expired token"));

        JUser jUser = authRepository.findByEmail(email)
                .orElseThrow(() -> new UnauthorizedException("Invalid or expired token"));

        JHost host = hostEventService.recordHostAndCheckBan(jUser, servletRequest);
        hostEventService.logHostEvent(host, "VERIFY_SUCCESS : token accepted", EventLogStatus.APPROVED, servletRequest);

        if (!jUser.getVerified()) {
            jUser.setVerified(true);
            authRepository.save(jUser);
            hostEventService.logHostEvent(host, "REGISTRATION_VERIFIED", EventLogStatus.APPROVED, servletRequest);
        }

        if (jUser.getStatus() == UserStatus.LOCKED) {
            jUser.setStatus(UserStatus.ACTIVE);
            authRepository.save(jUser);
            failedLoginTracker.reset(jUser.getId());
            hostEventService.logHostEvent(host, "UNLOCK_SUCCEEDED", EventLogStatus.APPROVED, servletRequest);
        }

        Optional<String> pendingEmail = verificationCodeStore.getPendingEmailByToken(tokenStr);
        if (pendingEmail.isPresent()) {
            jUser.setEmail(pendingEmail.get());
            authRepository.save(jUser);
            hostEventService.logHostEvent(host, "EMAIL_CHANGE_SUCCEEDED", EventLogStatus.APPROVED, servletRequest);
            verificationCodeStore.deletePendingEmailByToken(tokenStr);
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

        authMailService.sendVerificationLink(normalizedEmail, jUser.getFirstName(), jUser.getLastName(),
                jUser.getUsername(), "Email Verification", "mail/verification", servletRequest);

        return new MessageBody("A verification link has been sent to your email");
    }
}
