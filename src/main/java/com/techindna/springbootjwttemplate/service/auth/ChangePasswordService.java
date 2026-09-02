package com.techindna.springbootjwttemplate.service.auth;

import com.techindna.springbootjwttemplate.dto.ChangePasswordInput;
import com.techindna.springbootjwttemplate.dto.MessageBody;
import com.techindna.springbootjwttemplate.entity.enums.EventLogStatus;
import com.techindna.springbootjwttemplate.exception.http.ForbiddenException;
import com.techindna.springbootjwttemplate.exception.http.UnauthorizedException;
import com.techindna.springbootjwttemplate.exception.http.UnprocessableContentException;
import com.techindna.springbootjwttemplate.repository.AuthRepository;
import com.techindna.springbootjwttemplate.repository.model.JHost;
import com.techindna.springbootjwttemplate.repository.model.JUser;
import com.techindna.springbootjwttemplate.service.ABACRulesService;
import com.techindna.springbootjwttemplate.service.HostEventService;
import com.techindna.springbootjwttemplate.service.auth.AuthService;
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
public class ChangePasswordService {

    private final AuthRepository authRepository;
    private final ABACRulesService abacRulesService;
    private final AuthValidator authValidator;
    private final PasswordEncoder passwordEncoder;
    private final HostEventService hostEventService;
    private final AuthService authService;

    @Transactional(noRollbackFor = {UnprocessableContentException.class, ForbiddenException.class, UnauthorizedException.class})
    public MessageBody changePassword(ChangePasswordInput request, HttpServletRequest servletRequest, Authentication auth) {
        JUser jUser = authService.getAuthenticatedUser();

        abacRulesService.enforceIpBinding(auth, servletRequest);
        JHost userHost = hostEventService.recordHostAndCheckBan(jUser, servletRequest);

        authService.requireActiveVerifiedAccount(userHost, jUser, "PASSWORD_CHANGE", servletRequest);

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

        authService.sendTemplatedEmail(jUser.getEmail(), "Password Changed", "mail/password-change", variables, servletRequest);

        return new MessageBody("Password changed successfully");
    }
}