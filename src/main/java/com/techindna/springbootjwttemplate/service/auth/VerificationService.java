package com.techindna.springbootjwttemplate.service.auth;

import com.techindna.springbootjwttemplate.dto.VerifyRegistrationResponse;
import com.techindna.springbootjwttemplate.entity.User;
import com.techindna.springbootjwttemplate.entity.enums.EventLogStatus;
import com.techindna.springbootjwttemplate.entity.enums.UserStatus;
import com.techindna.springbootjwttemplate.exception.http.UnauthorizedException;
import com.techindna.springbootjwttemplate.mapper.UserMapper;
import com.techindna.springbootjwttemplate.repository.AuthRepository;
import com.techindna.springbootjwttemplate.repository.model.JHost;
import com.techindna.springbootjwttemplate.repository.model.JUser;
import com.techindna.springbootjwttemplate.security.jwt.JwtTokenProvider;
import com.techindna.springbootjwttemplate.service.GeoIpService;
import com.techindna.springbootjwttemplate.service.HostEventService;
import com.techindna.springbootjwttemplate.service.redis.FailedLoginTracker;
import com.techindna.springbootjwttemplate.service.redis.VerificationCodeStore;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class VerificationService {

    private final AuthRepository authRepository;
    private final UserMapper userMapper;
    private final VerificationCodeStore verificationCodeStore;
    private final JwtTokenProvider jwtTokenProvider;
    private final GeoIpService geoIpService;
    private final HostEventService hostEventService;
    private final FailedLoginTracker failedLoginTracker;

    @Transactional
    public VerifyRegistrationResponse verify(UUID token, HttpServletRequest servletRequest) {
        String tokenStr = token.toString();
        String email = verificationCodeStore.getEmailByToken(tokenStr)
                .orElseThrow(() -> new UnauthorizedException("Invalid or expired token"));

        JUser jUser = authRepository.findByEmail(email)
                .orElseThrow(() -> new UnauthorizedException("Invalid or expired token"));

        JHost host = hostEventService.recordHostAndCheckBan(jUser, servletRequest);
        hostEventService.logHostEvent(host, "VERIFY_SUCCESS : token accepted", EventLogStatus.APPROVED, servletRequest);

        applyVerificationFlows(jUser, host, tokenStr, servletRequest);

        verificationCodeStore.deleteByToken(tokenStr);

        String clientIp = geoIpService.extractClientIp(servletRequest);
        String jwtToken = jwtTokenProvider.generateToken(jUser.getId().toString(), jUser.getRole().name(), clientIp);
        User user = userMapper.toDomain(jUser);

        return new VerifyRegistrationResponse(jwtToken, user);
    }

    private void applyVerificationFlows(JUser jUser, JHost host, String tokenStr, HttpServletRequest servletRequest) {
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
    }
}