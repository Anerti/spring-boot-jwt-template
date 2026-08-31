package com.techindna.springbootjwttemplate.mapper;

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
}
