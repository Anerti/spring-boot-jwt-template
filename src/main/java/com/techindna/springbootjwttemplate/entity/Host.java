package com.techindna.springbootjwttemplate.entity;

import com.techindna.springbootjwttemplate.entity.enums.HostStatus;
import java.time.Instant;
import java.util.UUID;

public record Host(
    UUID id,
    String ipAddress,
    HostStatus hostStatus,
    Instant lastSeenAt,
    Instant updatedAt
) {}
