package com.techindna.springbootjwttemplate.dto;

import com.techindna.springbootjwttemplate.entity.enums.HostStatus;
import java.time.Instant;
import java.util.UUID;

public record HostDetailResponse(
        UUID id,
        String ipAddress,
        HostStatus hostStatus,
        Instant lastSeenAt,
        Instant updatedAt,
        String city,
        String country,
        String countryCode,
        String continent,
        String subdivision,
        String postalCode,
        String timezone,
        Double latitude,
        Double longitude
) {}
