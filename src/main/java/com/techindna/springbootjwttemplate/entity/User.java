package com.techindna.springbootjwttemplate.entity;

import com.techindna.springbootjwttemplate.entity.enums.UserRole;
import java.time.Instant;
import java.util.UUID;

public record User(
    UUID id,
    String username,
    String firstName,
    String lastName,
    String email,
    UserRole role,
    Instant createdAt,
    Instant updatedAt
) {}
