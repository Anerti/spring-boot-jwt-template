package com.techindna.springbootjwttemplate.service;

import com.techindna.springbootjwttemplate.entity.User;
import com.techindna.springbootjwttemplate.exception.http.NotFoundException;
import com.techindna.springbootjwttemplate.mapper.UserMapper;
import com.techindna.springbootjwttemplate.repository.UserRepository;
import com.techindna.springbootjwttemplate.security.ResourcesAccessRules;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final ResourcesAccessRules resourcesAccessRules;

    public User getUserById(UUID userId) {
        var jUser = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found"));

        resourcesAccessRules.grantAccessFor(userId, jUser.getRole());

        return userMapper.toDomain(jUser);
    }
}
