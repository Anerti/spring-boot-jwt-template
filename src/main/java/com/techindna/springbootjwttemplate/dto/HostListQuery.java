package com.techindna.springbootjwttemplate.dto;

public record HostListQuery(
    String ipAddress,
    com.techindna.springbootjwttemplate.entity.enums.HostStatus status
) {}
