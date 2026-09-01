package com.techindna.springbootjwttemplate.mapper;

import com.techindna.springbootjwttemplate.entity.EventLog;
import com.techindna.springbootjwttemplate.repository.model.JEventLog;
import org.springframework.stereotype.Component;

@Component
public class EventLogMapper {

    public EventLog toDomain(JEventLog jEventLog) {
        return new EventLog(
                jEventLog.getId(),
                jEventLog.getHost().getId(),
                jEventLog.getUserAgent(),
                jEventLog.getStatus(),
                jEventLog.getDescription(),
                jEventLog.getCreatedAt()
        );
    }
}
