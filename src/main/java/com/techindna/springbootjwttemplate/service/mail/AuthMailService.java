package com.techindna.springbootjwttemplate.service.mail;

import com.techindna.springbootjwttemplate.entity.GeoIpResponse;
import com.techindna.springbootjwttemplate.entity.email.EmailDetails;
import com.techindna.springbootjwttemplate.service.GeoIpService;
import com.techindna.springbootjwttemplate.service.redis.VerificationCodeStore;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthMailService {

    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("MMM d, yyyy HH:mm 'UTC'").withZone(ZoneOffset.UTC);

    private final GeoIpService geoIpService;
    private final VerificationCodeStore verificationCodeStore;
    private final EmailService emailService;

    @Value("${app.base-url}")
    private String baseUrl;

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

    public String generateVerificationToken(String email) {
        String token = UUID.randomUUID().toString();
        verificationCodeStore.saveToken(email, token);
        return token;
    }

    public String verificationUrl(String token) {
        return String.format("%s/auth/verification/%s", baseUrl, token);
    }

    public void sendTemplatedEmail(String email, String subject, String template,
                                   Map<String, Object> vars, HttpServletRequest request) {
        addClientData(vars, request);
        emailService.sendMail(new EmailDetails(email, subject, template, vars));
    }
}