package com.techindna.springbootjwttemplate.service;

import com.techindna.springbootjwttemplate.dto.EventLogListQuery;
import com.techindna.springbootjwttemplate.dto.Meta;
import com.techindna.springbootjwttemplate.dto.PaginatedResponse;
import com.techindna.springbootjwttemplate.entity.EventLog;
import com.techindna.springbootjwttemplate.entity.enums.EventLogStatus;
import com.techindna.springbootjwttemplate.mapper.EventLogMapper;
import com.techindna.springbootjwttemplate.repository.LogRepository;
import com.techindna.springbootjwttemplate.repository.model.JEventLog;
import com.techindna.springbootjwttemplate.repository.model.JHost;
import com.techindna.springbootjwttemplate.repository.model.JUser;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class EventLogService {

    private final LogRepository logRepository;
    private final EventLogMapper eventLogMapper;
    private final ABACRulesService abacRulesService;
    private final HostEventService hostEventService;

    @Value("${app.pagination.default-page}")
    private int defaultPage;

    @Value("${app.pagination.default-size}")
    private int defaultSize;

    @Transactional
    public PaginatedResponse<EventLog> listEventLogs(
            UUID userId,
            EventLogListQuery query,
            int page,
            int size,
            HttpServletRequest request) {

        JUser juser = abacRulesService.grantAccessFor(userId, request);
        JHost userHost = hostEventService.recordHostAndCheckBan(juser, request);

        var pageable = eventLogMapper.toPageable(query, page, size, defaultPage, defaultSize);

        Instant startDate = query.startDate() != null ? query.startDate() : Instant.now();
        Instant endDate = query.endDate() != null ? query.endDate() : Instant.now();

        Page<JEventLog> resultPage = logRepository.search(userId, query.status(), startDate, endDate, pageable);
        List<EventLog> data = resultPage.getContent().stream().map(eventLogMapper::toDomain).toList();

        hostEventService.logHostEvent(userHost, "EVENT_LOG_LIST_REQUESTED", EventLogStatus.INFO, request);
        return new PaginatedResponse<>(
                data.isEmpty() ? null : data,
                new Meta(pageable.getPageNumber() + 1, pageable.getPageSize(), resultPage.getTotalElements())
        );
    }
}
