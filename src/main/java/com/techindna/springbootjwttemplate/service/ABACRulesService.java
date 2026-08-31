package com.techindna.springbootjwttemplate.service;

import com.techindna.springbootjwttemplate.entity.enums.UserRole;
import com.techindna.springbootjwttemplate.exception.http.ForbiddenException;
import com.techindna.springbootjwttemplate.repository.UserRepository;
import com.techindna.springbootjwttemplate.repository.model.JUser;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ABACRulesService {

    private final GeoIpService geoIpService;
    private final UserRepository userRepository;

    public JUser grantAccessFor(UUID userId, HttpServletRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String authenticatedUserId = auth.getName();
        String role = Objects.requireNonNull(auth.getAuthorities().iterator().next().getAuthority()).replace("ROLE_", "");

        var juser = userRepository.findById(userId)
                .orElseThrow(() -> new ForbiddenException("Insufficient privileges to access this resource"));

        enforceIpBinding(auth, request);
        if (!(authenticatedUserId.equals(juser.getId().toString()) || (role.equals("ADMIN") && juser.getRole().equals(UserRole.CUSTOMER)))){
            throw new ForbiddenException("Insufficient privileges to access this resource");
        }
        return juser;
    }

    public void enforceIpBinding(Authentication auth, HttpServletRequest request) {
        String jwtIp = (String) auth.getDetails();
        String currentIp = geoIpService.extractClientIp(request);
        if (currentIp != null && !currentIp.isBlank() && !currentIp.equals(jwtIp)) {
            throw new ForbiddenException("Session IP does not match current request");
        }
    }
}
