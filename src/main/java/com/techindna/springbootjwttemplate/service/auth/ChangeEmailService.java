package com.techindna.springbootjwttemplate.service.auth;

import com.techindna.springbootjwttemplate.dto.ChangeEmailInput;
import com.techindna.springbootjwttemplate.dto.MessageBody;
import com.techindna.springbootjwttemplate.entity.enums.EventLogStatus;
import com.techindna.springbootjwttemplate.entity.enums.UserStatus;
import com.techindna.springbootjwttemplate.exception.http.ConflictException;
import com.techindna.springbootjwttemplate.exception.http.ForbiddenException;
import com.techindna.springbootjwttemplate.exception.http.UnauthorizedException;
import com.techindna.springbootjwttemplate.exception.http.UnprocessableContentException;
import com.techindna.springbootjwttemplate.repository.AuthRepository;
import com.techindna.springbootjwttemplate.repository.model.JHost;
import com.techindna.springbootjwttemplate.repository.model.JUser;
import com.techindna.springbootjwttemplate.service.ABACRulesService;
import com.techindna.springbootjwttemplate.service.HostEventService;
import com.techindna.springbootjwttemplate.service.mail.AuthMailService;
import com.techindna.springbootjwttemplate.service.redis.VerificationCodeStore;
import com.techindna.springbootjwttemplate.validator.AuthValidator;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ChangeEmailService {

    private final AuthRepository authRepository;
    private final ABACRulesService abacRulesService;
    private final AuthValidator authValidator;
    private final PasswordEncoder passwordEncoder;
    private final HostEventService hostEventService;
    private final VerificationCodeStore verificationCodeStore;
    private final AuthMailService authMailService;

    @Transactional(noRollbackFor = {UnprocessableContentException.class, ForbiddenException.class, UnauthorizedException.class})
    public MessageBody changeEmail(ChangeEmailInput request, HttpServletRequest servletRequest, Authentication auth) {
        JUser jUser = authMailService.getAuthenticatedUser();

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
            return sendConfirmationEmail(jUser, userHost, normalizedNewEmail, servletRequest);
        }

        hostEventService.logHostEvent(userHost, "EMAIL_CHANGE_FAILED : wrong current password", EventLogStatus.SECURITY, servletRequest);
        throw new UnauthorizedException("Invalid credentials");
    }

    private MessageBody sendConfirmationEmail(JUser jUser, JHost userHost, String newEmail, HttpServletRequest servletRequest) {
        String token = authMailService.generateVerificationToken(jUser.getEmail());
        verificationCodeStore.savePendingEmail(token, newEmail);

        Map<String, Object> variables = new HashMap<>();
        variables.put("verificationUrl", authMailService.verificationUrl(token));
        variables.put("firstName", jUser.getFirstName());

        authMailService.sendTemplatedEmail(newEmail, "Confirm your new email address", "mail/change-email", variables, servletRequest);

        hostEventService.logHostEvent(userHost, "EMAIL_CHANGE_PENDING : confirmation sent to new address", EventLogStatus.INFO, servletRequest);
        return new MessageBody("A verification link has been sent to your new email address");
    }
}