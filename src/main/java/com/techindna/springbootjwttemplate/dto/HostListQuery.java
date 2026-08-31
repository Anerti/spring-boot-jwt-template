package com.techindna.springbootjwttemplate.dto;

import com.techindna.springbootjwttemplate.entity.enums.HostStatus;

public record HostListQuery(
    String ipAddress,
    HostStatus status,
    String sortByLastSeenAt
) {}
