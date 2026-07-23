package com.techindna.springbootjwttemplate.mapper;

import com.techindna.springbootjwttemplate.dto.RegisterInput;
import com.techindna.springbootjwttemplate.entity.enums.UserRole;
import com.techindna.springbootjwttemplate.repository.model.JUser;
import org.springframework.stereotype.Component;

@Component
public class AuthMapper {

    public JUser toEntity(RegisterInput request, String encodedPassword, String verificationCode) {
        JUser user = new JUser();
        user.setUsername(request.getUsername().strip());
        user.setPassword(encodedPassword);
        user.setFirstName(request.getFirstName().strip());
        user.setLastName(request.getLastName().strip());
        user.setEmail(request.getEmail().strip());
        user.setVerificationCode(verificationCode);
        user.setVerified(false);
        user.setRole(UserRole.CUSTOMER);
        return user;
    }
}
