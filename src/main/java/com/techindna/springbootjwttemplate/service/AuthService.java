package com.techindna.springbootjwttemplate.service;

import com.techindna.springbootjwttemplate.dto.ChangeEmailInput;
import com.techindna.springbootjwttemplate.dto.ChangePasswordInput;
import com.techindna.springbootjwttemplate.dto.MessageBody;
import com.techindna.springbootjwttemplate.dto.UnlockAccountInput;
import com.techindna.springbootjwttemplate.dto.VerifyRegistrationResponse;
import com.techindna.springbootjwttemplate.entity.User;
import com.techindna.springbootjwttemplate.entity.enums.EventLogStatus;
import com.techindna.springbootjwttemplate.entity.enums.UserStatus;
import com.techindna.springbootjwttemplate.exception.http.ConflictException;
import com.techindna.springbootjwttemplate.exception.http.ForbiddenException;
import com.techindna.springbootjwttemplate.exception.http.UnauthorizedException;
import com.techindna.springbootjwttemplate.exception.http.UnprocessableContentException;
import com.techindna.springbootjwttemplate.mapper.UserMapper;
import com.techindna.springbootjwttemplate.repository.AuthRepository;
import com.techindna.springbootjwttemplate.repository.model.JHost;
import com.techindna.springbootjwttemplate.repository.model.JUser;
import com.techindna.springbootjwttemplate.service.mail.AuthMailService;
import com.techindna.springbootjwttemplate.service.redis.FailedLoginTracker;
import com.techindna.springbootjwttemplate.service.redis.VerificationCodeStore;
import com.techindna.springbootjwttemplate.validator.DataValidator;
import com.techindna.springbootjwttemplate.validator.AuthValidator;
import com.techindna.springbootjwttemplate.security.jwt.JwtTokenProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.*;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthRepository authRepository;
    private final ABACRulesService abacRulesService;
    private final UserMapper userMapper;
    private final AuthValidator authValidator;
    private final DataValidator dataValidator;
    private final PasswordEncoder passwordEncoder;
    private final VerificationCodeStore verificationCodeStore;
    private final JwtTokenProvider jwtTokenProvider;
    private final GeoIpService geoIpService;
    private final HostEventService hostEventService;
    private final FailedLoginTracker failedLoginTracker;
    private final AuthMailService authMailService;

    @Transactional(noRollbackFor = {UnprocessableContentException.class, ForbiddenException.class, UnauthorizedException.class})
    public MessageBody changePassword(ChangePasswordInput request, HttpServletRequest servletRequest, Authentication auth) {
        JUser jUser = getAuthenticatedUser();

        abacRulesService.enforceIpBinding(auth, servletRequest);
        JHost userHost = hostEventService.recordHostAndCheckBan(jUser, servletRequest);

        authMailService.requireActiveVerifiedAccount(userHost, jUser, "PASSWORD_CHANGE", servletRequest);

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

        authMailService.sendTemplatedEmail(jUser.getEmail(), "Password Changed", "mail/password-change", variables, servletRequest);

        return new MessageBody("Password changed successfully");
    }

    @Transactional(noRollbackFor = {UnprocessableContentException.class, ForbiddenException.class, UnauthorizedException.class})
    public MessageBody changeEmail(ChangeEmailInput request, HttpServletRequest servletRequest, Authentication auth) {
        JUser jUser = getAuthenticatedUser();

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
            String token = authMailService.generateVerificationToken(jUser.getEmail());
            verificationCodeStore.savePendingEmail(token, normalizedNewEmail);

            Map<String, Object> variables = new HashMap<>();
            variables.put("verificationUrl", authMailService.verificationUrl(token));
            variables.put("firstName", jUser.getFirstName());

            authMailService.sendTemplatedEmail(normalizedNewEmail, "Confirm your new email address", "mail/change-email", variables, servletRequest);

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

        String token = authMailService.generateVerificationToken(jUser.getEmail());

        Map<String, Object> variables = new HashMap<>();
        variables.put("firstName", jUser.getFirstName());
        variables.put("verificationUrl", authMailService.verificationUrl(token));

        authMailService.sendTemplatedEmail(jUser.getEmail(), "Account Unlock", "mail/unlock-account", variables, servletRequest);

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

    private JUser getAuthenticatedUser() {
        String userId = Objects.requireNonNull(SecurityContextHolder.getContext().getAuthentication()).getName();
        return authRepository.findById(UUID.fromString(userId))
                .orElseThrow(() -> new UnauthorizedException("Invalid credentials"));
    }
}
