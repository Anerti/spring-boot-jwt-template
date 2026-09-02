package com.techindna.springbootjwttemplate.service.mail;

import com.techindna.springbootjwttemplate.entity.GeoIpResponse;
import com.techindna.springbootjwttemplate.service.GeoIpService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AuthMailService {

    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("MMM d, yyyy HH:mm 'UTC'").withZone(ZoneOffset.UTC);

    private final GeoIpService geoIpService;

    public void addClientData(Map<String, Object> variables, HttpServletRequest request) {
        String clientIp = geoIpService.extractClientIp(request);
        String userAgent = request.getHeader("User-Agent");

        variables.put("clientIp", clientIp);
        variables.put("userAgent", userAgent != null ? userAgent : "Unknown");
        variables.put("time", DATE_FORMAT.format(Instant.now()));

        GeoIpResponse geo = geoIpService.resolveGeoData(request);
        variables.put("city", geo != null && geo.city() != null ? geo.city() : "N/A");
        variables.put("country", geo != null && geo.country() != null ? geo.country() : "N/A");
        variables.put("countryCode", geo != null && geo.countryCode() != null ? geo.countryCode() : "N/A");
        variables.put("timezone", geo != null && geo.timezone() != null ? geo.timezone() : "N/A");
        variables.put("latitude", geo != null && geo.latitude() != null ? String.format("%.6f", geo.latitude()) : "N/A");
        variables.put("longitude", geo != null && geo.longitude() != null ? String.format("%.6f", geo.longitude()) : "N/A");
    }
}
