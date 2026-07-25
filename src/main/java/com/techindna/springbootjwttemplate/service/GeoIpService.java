package com.techindna.springbootjwttemplate.service;

import com.maxmind.geoip2.DatabaseReader;
import com.maxmind.geoip2.exception.AddressNotFoundException;
import com.maxmind.geoip2.exception.GeoIp2Exception;
import com.maxmind.geoip2.model.CityResponse;
import com.techindna.springbootjwttemplate.entity.GeoIpResponse;
import com.techindna.springbootjwttemplate.exception.http.BadRequestException;
import com.techindna.springbootjwttemplate.exception.http.NotFoundException;
import com.techindna.springbootjwttemplate.mapper.GeoIpMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;

@Service
@RequiredArgsConstructor
public class GeoIpService {

    private final DatabaseReader geoIpCityReader;
    private final GeoIpMapper geoIpMapper;

    public GeoIpResponse lookup(String ip) {
        try {
            InetAddress address = InetAddress.getByName(ip);
            CityResponse response = geoIpCityReader.city(address);
            return geoIpMapper.toGeoIpResponse(response, ip);
        } catch (UnknownHostException e) {
            throw new BadRequestException("Invalid IP address format");
        } catch (AddressNotFoundException e) {
            throw new NotFoundException("IP address not found in database");
        } catch (IOException | GeoIp2Exception e) {
            throw new RuntimeException("Unable to resolve IP address", e);
        }
    }

    public String extractClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");

        return (xForwardedFor != null && !xForwardedFor.isBlank()) ?
                xForwardedFor.split(",")[0].trim() :
                request.getRemoteAddr();
    }
}
