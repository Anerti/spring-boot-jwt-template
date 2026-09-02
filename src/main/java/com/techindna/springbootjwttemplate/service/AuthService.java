package com.techindna.springbootjwttemplate.service;

import com.techindna.springbootjwttemplate.dto.MessageBody;
import com.techindna.springbootjwttemplate.dto.UnlockAccountInput;
import com.techindna.springbootjwttemplate.dto.VerifyRegistrationResponse;
import com.techindna.springbootjwttemplate.entity.User;
import com.techindna.springbootjwttemplate.entity.enums.EventLogStatus;
import com.techindna.springbootjwttemplate.entity.enums.UserStatus;
import com.techindna.springbootjwttemplate.exception.http.ForbiddenException;
import com.techindna.springbootjwttemplate.exception.http.UnauthorizedException;
import com.techindna.springbootjwttemplate.mapper.UserMapper;
import com.techindna.springbootjwttemplate.repository.AuthRepository;
import com.techindna.springbootjwttemplate.repository.model.JHost;
import com.techindna.springbootjwttemplate.repository.model.JUser;
import com.techindna.springbootjwttemplate.service.mail.AuthMailService;
import com.techindna.springbootjwttemplate.service.redis.FailedLoginTracker;
import com.techindna.springbootjwttemplate.service.redis.VerificationCodeStore;
import com.techindna.springbootjwttemplate.validator.DataValidator;
import com.techindna.springbootjwttemplate.security.jwt.JwtTokenProvider;

import java.util.*;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthRepository authRepository;
    private final UserMapper userMapper;
    private final DataValidator dataValidator;
    private final VerificationCodeStore verificationCodeStore;
    private final JwtTokenProvider jwtTokenProvider;
    private final GeoIpService geoIpService;
    private final HostEventService hostEventService;
    private final FailedLoginTracker failedLoginTracker;
    private final AuthMailService authMailService;

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
}
