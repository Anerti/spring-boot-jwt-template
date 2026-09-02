package com.techindna.springbootjwttemplate.service.auth;

import com.techindna.springbootjwttemplate.dto.LoginInput;
import com.techindna.springbootjwttemplate.dto.MessageBody;
import com.techindna.springbootjwttemplate.entity.enums.EventLogStatus;
import com.techindna.springbootjwttemplate.entity.enums.UserStatus;
import com.techindna.springbootjwttemplate.exception.http.ForbiddenException;
import com.techindna.springbootjwttemplate.exception.http.UnauthorizedException;
import com.techindna.springbootjwttemplate.repository.AuthRepository;
import com.techindna.springbootjwttemplate.repository.model.JHost;
import com.techindna.springbootjwttemplate.repository.model.JUser;
import com.techindna.springbootjwttemplate.service.HostEventService;
import com.techindna.springbootjwttemplate.service.mail.AuthMailService;
import com.techindna.springbootjwttemplate.service.redis.FailedLoginTracker;
import com.techindna.springbootjwttemplate.validator.AuthValidator;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class LoginService {

    private static final int MAX_FAILED_LOGIN_ATTEMPTS = 5;

    private final AuthRepository authRepository;
    private final AuthValidator authValidator;
    private final PasswordEncoder passwordEncoder;
    private final HostEventService hostEventService;
    private final FailedLoginTracker failedLoginTracker;
    private final AuthMailService authMailService;

    @Transactional(noRollbackFor = {ForbiddenException.class, UnauthorizedException.class})
    public MessageBody login(LoginInput request, HttpServletRequest servletRequest) {
        authValidator.validateLogin(request);

        JUser jUser = request.getEmail() != null && !request.getEmail().isBlank() ?
                authRepository.findByEmail(request.getEmail().strip().toLowerCase())
                        .orElseThrow(() -> new UnauthorizedException("Invalid credentials")) :
                authRepository.findByUsername(request.getUsername().strip())
                        .orElseThrow(() -> new UnauthorizedException("Invalid credentials"));

        JHost host = hostEventService.recordHostAndCheckBan(jUser, servletRequest);

        authMailService.requireActiveVerifiedAccount(host, jUser, "LOGIN", servletRequest);

        String token = authMailService.generateVerificationToken(jUser.getEmail());
        if (passwordEncoder.matches(request.getPassword(), jUser.getPassword())) {
            return sendLoginVerification(jUser, host, token, servletRequest);
        }

        int failedCount = failedLoginTracker.increment(jUser.getId());
        hostEventService.logHostEvent(host, "LOGIN_FAILED : invalid credentials", EventLogStatus.SECURITY, servletRequest);

        if (failedCount == MAX_FAILED_LOGIN_ATTEMPTS) {
            jUser.setStatus(UserStatus.LOCKED);
            authRepository.save(jUser);
            hostEventService.logHostEvent(host, "ACCOUNT_LOCKED : repeated failed login attempts", EventLogStatus.SECURITY, servletRequest);

            Map<String, Object> variables = new HashMap<>();
            variables.put("firstName", jUser.getFirstName());

            authMailService.sendTemplatedEmail(jUser.getEmail(), "Security Alert: Your account has been locked", "mail/account-locked", variables, servletRequest);
            throw new ForbiddenException("Account has been locked due to multiple failed logins");
        }

        throw new UnauthorizedException(String.format("Invalid credentials. %s attempt(s) left", MAX_FAILED_LOGIN_ATTEMPTS - failedCount));
    }

    private MessageBody sendLoginVerification(JUser jUser, JHost host, String token, HttpServletRequest servletRequest) {
        hostEventService.logHostEvent(host, "LOGIN_SUCCESS : verification link sent", EventLogStatus.APPROVED, servletRequest);

        Map<String, Object> variables = new HashMap<>();
        variables.put("firstName", jUser.getFirstName());
        variables.put("verificationUrl", authMailService.verificationUrl(token));

        authMailService.sendTemplatedEmail(jUser.getEmail(), "Login Verification", "mail/login-verification", variables, servletRequest);
        return new MessageBody("A verification link has been sent to your email");
    }
}