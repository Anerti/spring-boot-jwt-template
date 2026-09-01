package com.techindna.springbootjwttemplate.service;

import com.maxmind.geoip2.DatabaseReader;
import com.maxmind.geoip2.exception.AddressNotFoundException;
import com.maxmind.geoip2.exception.GeoIp2Exception;
import com.maxmind.geoip2.model.CityResponse;
import com.techindna.springbootjwttemplate.entity.GeoIpResponse;
import com.techindna.springbootjwttemplate.exception.http.BadRequestException;
import com.techindna.springbootjwttemplate.exception.http.NotFoundException;
import com.techindna.springbootjwttemplate.mapper.GeoIpMapper;
import com.techindna.springbootjwttemplate.validator.DataValidator;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.InetAddress;

@Service
@RequiredArgsConstructor
public class GeoIpService {

    private final DatabaseReader geoIpCityReader;
    private final GeoIpMapper geoIpMapper;
    private final DataValidator dataValidator;

    @Value("${geoip.trust-x-forwarded-for}")
    private boolean trustXForwardedFor;

    private static final Logger log = LoggerFactory.getLogger(GeoIpService.class);

    public GeoIpResponse lookup(String ip) {
        if (!dataValidator.isValidAddressFormat(ip)) {
            throw new BadRequestException("Invalid IP address format");
        }

        try {
            InetAddress address = InetAddress.getByName(ip);
            CityResponse response = geoIpCityReader.city(address);
            return geoIpMapper.toGeoIpResponse(response, ip);
        } catch (AddressNotFoundException e) {
            throw new NotFoundException("IP address not found in database");
        } catch (IOException | GeoIp2Exception e) {
            throw new RuntimeException("Unable to resolve IP address", e);
        }
    }

    public String extractClientIp(HttpServletRequest request) {
        if (!trustXForwardedFor) {
            return request.getRemoteAddr();
        }

        String xForwardedFor = request.getHeader("X-Forwarded-For");
        return (xForwardedFor != null && !xForwardedFor.isBlank()) ?
                xForwardedFor.split(",")[0].trim() :
                request.getRemoteAddr();
    }

    public GeoIpResponse lookupOrNull(String ip) {
        try {
            return lookup(ip);
        } catch (RuntimeException e) {
            log.warn("GeoIP lookup failed for IP {}: {}", ip, e.getMessage());
            return null;
        }
    }

    public GeoIpResponse resolveGeoData(HttpServletRequest request) {
        return lookupOrNull(extractClientIp(request));
    }

}
