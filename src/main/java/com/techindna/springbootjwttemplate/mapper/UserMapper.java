package com.techindna.springbootjwttemplate.mapper;

import com.techindna.springbootjwttemplate.dto.RegisterInput;
import com.techindna.springbootjwttemplate.entity.User;
import com.techindna.springbootjwttemplate.entity.enums.UserRole;
import com.techindna.springbootjwttemplate.entity.enums.UserStatus;
import com.techindna.springbootjwttemplate.repository.model.JUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class UserMapper {

    public JUser toEntity(RegisterInput request, String encodedPassword) {
        return JUser.builder()
                .username(request.getUsername().strip())
                .password(encodedPassword)
                .firstName(request.getFirstName().strip())
                .lastName(request.getLastName().strip())
                .email(request.getEmail().strip().toLowerCase())
                .verified(false)
                .role(UserRole.CUSTOMER)
                .status(UserStatus.ACTIVE)
                .build();
    }

    public User toDomain(JUser jUser) {
        return new User(
                jUser.getId(),
                jUser.getUsername(),
                jUser.getFirstName(),
                jUser.getLastName(),
                jUser.getEmail(),
                jUser.getRole(),
                jUser.getStatus(),
                jUser.getCreatedAt(),
                jUser.getUpdatedAt()
        );
    }
}
