package com.techindna.springbootjwttemplate.dto;

import com.techindna.springbootjwttemplate.entity.enums.EventLogStatus;
import java.time.Instant;

public record EventLogListQuery(
    EventLogStatus status,
    Instant startDate,
    Instant endDate,
    String sortByCreatedAt
) {}
