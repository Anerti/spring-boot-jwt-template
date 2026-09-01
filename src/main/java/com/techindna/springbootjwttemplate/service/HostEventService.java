package com.techindna.springbootjwttemplate.service;

import com.techindna.springbootjwttemplate.entity.enums.EventLogStatus;
import com.techindna.springbootjwttemplate.entity.enums.HostStatus;
import com.techindna.springbootjwttemplate.exception.http.ForbiddenException;
import com.techindna.springbootjwttemplate.repository.HostRepository;
import com.techindna.springbootjwttemplate.repository.LogRepository;
import com.techindna.springbootjwttemplate.repository.model.JEventLog;
import com.techindna.springbootjwttemplate.repository.model.JHost;
import com.techindna.springbootjwttemplate.repository.model.JUser;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class HostEventService {

    private final HostRepository hostRepository;
    private final LogRepository logRepository;
    private final GeoIpService geoIpService;

    @Transactional
    public JHost recordHostAndCheckBan(JUser user, HttpServletRequest servletRequest) {
        String ipAddress = geoIpService.extractClientIp(servletRequest);

        JHost host = hostRepository.findByIpAddressAndUser_Id(ipAddress, user.getId())
                .orElseGet(() -> JHost.builder()
                        .user(user)
                        .ipAddress(ipAddress)
                        .lastSeenAt(Instant.now())
                        .build());

        if (host.getStatus() == HostStatus.BANNED) {
            throw new ForbiddenException(String.format("Host %s is banned from accessing this account", ipAddress));
        }

        return hostRepository.saveAndFlush(host);
    }

    @Transactional
    public void logHostEvent(JHost host, String description, EventLogStatus status, HttpServletRequest servletRequest) {
        String rawUserAgent = servletRequest.getHeader("User-Agent");
        JEventLog logEntry = JEventLog.builder()
                .host(host)
                .userAgent((rawUserAgent != null && !rawUserAgent.isBlank()) ? rawUserAgent : "Unknown")
                .description(description)
                .status(status)
                .build();
        logRepository.save(logEntry);
    }
}
