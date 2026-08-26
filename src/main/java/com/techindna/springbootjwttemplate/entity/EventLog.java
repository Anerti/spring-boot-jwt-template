package com.techindna.springbootjwttemplate.entity;

import java.time.Instant;
import java.util.UUID;

public record EventLog(
    UUID id,
    UUID hostId,
    String description,
    Instant createdAt
) {}
