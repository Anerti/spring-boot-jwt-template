package com.techindna.springbootjwttemplate.service;

import com.techindna.springbootjwttemplate.dto.UpdateUserInput;
import com.techindna.springbootjwttemplate.entity.User;
import com.techindna.springbootjwttemplate.exception.http.ConflictException;
import com.techindna.springbootjwttemplate.exception.http.ForbiddenException;
import com.techindna.springbootjwttemplate.exception.http.NotFoundException;
import com.techindna.springbootjwttemplate.mapper.UserMapper;
import com.techindna.springbootjwttemplate.repository.UserRepository;
import com.techindna.springbootjwttemplate.security.ResourcesAccessRules;
import com.techindna.springbootjwttemplate.validator.UserValidator;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final ResourcesAccessRules resourcesAccessRules;
    private final UserValidator userValidator;

    public User getUserById(UUID userId, HttpServletRequest request) {
        var jUser = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found"));

        resourcesAccessRules.grantAccessFor(userId, jUser.getRole(), request);

        return userMapper.toDomain(jUser);
    }

    @Transactional
    public User updateUser(UUID userId, UpdateUserInput input, HttpServletRequest request) {
        var jUser = userRepository.findById(userId)
                .orElseThrow(() -> new ForbiddenException("Insufficient privileges to access this resource"));

        resourcesAccessRules.grantAccessFor(userId, jUser.getRole(), request);

        userValidator.validateAndApplyUpdate(input, jUser);

        try {
            userRepository.saveAndFlush(jUser);
        } catch (DataIntegrityViolationException e) {
            String constraint = e.getMostSpecificCause().getMessage();
            if (constraint != null && constraint.contains("username")) {
                throw new ConflictException("Cannot use this username");
            }
            throw e;
        }

        return userMapper.toDomain(jUser);
    }
}
