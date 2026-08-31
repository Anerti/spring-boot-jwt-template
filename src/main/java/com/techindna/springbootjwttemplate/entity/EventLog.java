package com.techindna.springbootjwttemplate.entity;

import com.techindna.springbootjwttemplate.entity.enums.EventLogStatus;
import java.time.Instant;
import java.util.UUID;

public record EventLog(
    UUID id,
    UUID hostId,
    String userAgent,
    EventLogStatus status,
    String description,
    Instant createdAt
) {}
