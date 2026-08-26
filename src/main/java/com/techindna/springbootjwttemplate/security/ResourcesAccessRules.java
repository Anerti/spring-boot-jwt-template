package com.techindna.springbootjwttemplate.security;

import com.techindna.springbootjwttemplate.entity.enums.UserRole;
import com.techindna.springbootjwttemplate.exception.http.ForbiddenException;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Objects;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class ResourcesAccessRules {

    public void grantAccessFor(UUID targetId, UserRole targetRole, HttpServletRequest request) {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        UUID requesterId = UUID.fromString(auth.getName());
        var authorities = auth.getAuthorities();

        if (authorities.isEmpty()) {
            throw new ForbiddenException("Insufficient privileges to access this resource");
        }

        UserRole requesterRole = UserRole.valueOf(
                Objects.requireNonNull(authorities.iterator().next().getAuthority()).replace("ROLE_", ""));

        if (requesterId.equals(targetId)) {
            return;
        }

        if ((requesterRole == UserRole.CUSTOMER) ||
                (requesterRole == UserRole.ADMIN && targetRole == UserRole.ADMIN)) {
            throw new ForbiddenException("Insufficient privileges to access this resource");
        }

        enforceIpBinding(auth, request);
    }

    private void enforceIpBinding(Authentication auth, HttpServletRequest request) {
        String jwtIp = (String) auth.getDetails();
        String currentIp = request.getHeader("X-Forwarded-For");
        if (currentIp == null || currentIp.isBlank()) {
            currentIp = request.getRemoteAddr();
        }
        if (currentIp != null && !currentIp.isBlank() && !currentIp.equals(jwtIp)) {
            throw new ForbiddenException("Session IP does not match current request");
        }
    }
}
