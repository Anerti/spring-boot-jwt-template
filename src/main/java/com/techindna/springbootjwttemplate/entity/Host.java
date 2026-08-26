package com.techindna.springbootjwttemplate.entity;

import com.techindna.springbootjwttemplate.entity.enums.HostStatus;
import java.time.Instant;
import java.util.UUID;

public record Host(
    UUID id,
    UUID userId,
    String ipAddress,
    String userAgent,
    HostStatus status,
    String description,
    Instant createdAt,
    Instant updatedAt
) {}
