package com.techindna.springbootjwttemplate.security;

import com.techindna.springbootjwttemplate.entity.enums.UserRole;
import com.techindna.springbootjwttemplate.exception.http.ForbiddenException;


import java.util.Objects;
import java.util.UUID;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class ResourcesAccessRules {

    public void grantAccessFor(UUID targetId, UserRole targetRole) {
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

    }
}
