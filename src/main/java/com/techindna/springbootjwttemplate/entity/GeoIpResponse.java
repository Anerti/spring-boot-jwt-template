package com.techindna.springbootjwttemplate.entity;

public record GeoIpResponse(
    String ip,
    String city,
    String country,
    String countryCode,
    String continent,
    String subdivision,
    String postalCode,
    String timezone,
    double latitude,
    double longitude
) {}
