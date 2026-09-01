package com.techindna.springbootjwttemplate.mapper;

import com.techindna.springbootjwttemplate.dto.HostDetailResponse;
import com.techindna.springbootjwttemplate.entity.GeoIpResponse;
import com.techindna.springbootjwttemplate.entity.Host;
import com.techindna.springbootjwttemplate.repository.model.JHost;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class HostMapper {

    public Host toDomain(JHost jHost) {
        return new Host(
                jHost.getId(),
                jHost.getIpAddress(),
                jHost.getStatus(),
                jHost.getLastSeenAt(),
                jHost.getUpdatedAt()
        );
    }

    public HostDetailResponse toDetailResponse(JHost jHost, GeoIpResponse geo) {
        return new HostDetailResponse(
                jHost.getId(),
                jHost.getIpAddress(),
                jHost.getStatus(),
                jHost.getLastSeenAt(),
                jHost.getUpdatedAt(),
                geo != null ? geo.city() : null,
                geo != null ? geo.country() : null,
                geo != null ? geo.countryCode() : null,
                geo != null ? geo.continent() : null,
                geo != null ? geo.subdivision() : null,
                geo != null ? geo.postalCode() : null,
                geo != null ? geo.timezone() : null,
                geo != null ? geo.latitude() : null,
                geo != null ? geo.longitude() : null
        );
    }
}
