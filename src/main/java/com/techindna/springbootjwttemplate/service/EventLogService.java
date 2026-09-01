package com.techindna.springbootjwttemplate.service;

import com.techindna.springbootjwttemplate.dto.EventLogListQuery;
import com.techindna.springbootjwttemplate.dto.Meta;
import com.techindna.springbootjwttemplate.dto.PaginatedResponse;
import com.techindna.springbootjwttemplate.entity.EventLog;
import com.techindna.springbootjwttemplate.mapper.EventLogMapper;
import com.techindna.springbootjwttemplate.repository.LogRepository;
import com.techindna.springbootjwttemplate.repository.model.JEventLog;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class EventLogService {

    private final LogRepository logRepository;
    private final EventLogMapper eventLogMapper;
    private final ABACRulesService abacRulesService;

    @Value("${app.pagination.default-page}")
    private int defaultPage;

    @Value("${app.pagination.default-size}")
    private int defaultSize;

    @Transactional(readOnly = true)
    public PaginatedResponse<EventLog> listEventLogs(
            UUID userId,
            EventLogListQuery query,
            int page,
            int size,
            HttpServletRequest request) {

        abacRulesService.grantAccessFor(userId, request);

        page = page < 1 ? defaultPage : page;
        size = (size < 1 || size > 100) ? defaultSize : size;

        String sortByCreatedAt = query.sortByCreatedAt() != null ? query.sortByCreatedAt() : "desc";

        Sort sort = Sort.by(
                "asc".equalsIgnoreCase(sortByCreatedAt) ? Sort.Direction.ASC : Sort.Direction.DESC,
                "createdAt");
        Pageable pageable = PageRequest.of(page - 1, size, sort);

        Instant startDate = query.startDate() != null ? query.startDate() : Instant.now();
        Instant endDate = query.endDate() != null ? query.endDate() : Instant.now();

        Page<JEventLog> resultPage = logRepository.search(userId, query.status(), startDate, endDate, pageable);
        List<EventLog> data = resultPage.getContent().stream().map(eventLogMapper::toDomain).toList();

        return new PaginatedResponse<>(
                data.isEmpty() ? null : data,
                new Meta(page, size, resultPage.getTotalElements())
        );
    }
}
