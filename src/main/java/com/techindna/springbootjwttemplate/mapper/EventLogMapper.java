package com.techindna.springbootjwttemplate.mapper;

import com.techindna.springbootjwttemplate.dto.EventLogListQuery;
import com.techindna.springbootjwttemplate.entity.EventLog;
import com.techindna.springbootjwttemplate.repository.model.JEventLog;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
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

    public Pageable toPageable(EventLogListQuery query, int page, int size, int defaultPage, int defaultSize) {
        int normalizedPage = page < 1 ? defaultPage : page;
        int normalizedSize = (size < 1 || size > 100) ? defaultSize : size;
        String sortBy = query.sortByCreatedAt() != null ? query.sortByCreatedAt() : "desc";
        Sort sort = Sort.by("asc".equalsIgnoreCase(sortBy) ? Sort.Direction.ASC : Sort.Direction.DESC, "createdAt");
        return PageRequest.of(normalizedPage - 1, normalizedSize, sort);
    }
}
