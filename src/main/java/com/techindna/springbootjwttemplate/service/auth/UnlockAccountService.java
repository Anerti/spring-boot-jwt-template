package com.techindna.springbootjwttemplate.service.auth;

import com.techindna.springbootjwttemplate.dto.MessageBody;
import com.techindna.springbootjwttemplate.dto.UnlockAccountInput;
import com.techindna.springbootjwttemplate.entity.enums.EventLogStatus;
import com.techindna.springbootjwttemplate.entity.enums.UserStatus;
import com.techindna.springbootjwttemplate.exception.http.ForbiddenException;
import com.techindna.springbootjwttemplate.repository.AuthRepository;
import com.techindna.springbootjwttemplate.repository.model.JHost;
import com.techindna.springbootjwttemplate.repository.model.JUser;
import com.techindna.springbootjwttemplate.service.HostEventService;
import com.techindna.springbootjwttemplate.service.mail.AuthMailService;
import com.techindna.springbootjwttemplate.validator.DataValidator;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class UnlockAccountService {

    private final AuthRepository authRepository;
    private final DataValidator dataValidator;
    private final HostEventService hostEventService;
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
}