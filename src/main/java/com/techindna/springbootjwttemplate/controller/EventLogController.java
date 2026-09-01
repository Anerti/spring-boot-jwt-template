package com.techindna.springbootjwttemplate.controller;

import com.techindna.springbootjwttemplate.dto.EventLogListQuery;
import com.techindna.springbootjwttemplate.dto.PaginatedResponse;
import com.techindna.springbootjwttemplate.entity.EventLog;
import com.techindna.springbootjwttemplate.service.EventLogService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/users/{userId}/event-log")
@RequiredArgsConstructor
public class EventLogController {

    private final EventLogService eventLogService;

    @GetMapping
    public ResponseEntity<PaginatedResponse<EventLog>> listEventLogs(
            @PathVariable UUID userId,
            EventLogListQuery query,
            @RequestParam(defaultValue = "${app.pagination.default-page}") int page,
            @RequestParam(defaultValue = "${app.pagination.default-size}") int size,
            HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.OK).body(eventLogService.listEventLogs(
                userId, query, page, size, request));
    }
}
