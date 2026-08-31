package com.techindna.springbootjwttemplate.controller;

import com.techindna.springbootjwttemplate.dto.HostListQuery;
import com.techindna.springbootjwttemplate.dto.PaginatedResponse;
import com.techindna.springbootjwttemplate.entity.Host;
import com.techindna.springbootjwttemplate.service.HostService;
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
@RequestMapping("/users/{userId}/hosts")
@RequiredArgsConstructor
public class HostController {

    private final HostService hostService;

    @GetMapping
    public ResponseEntity<PaginatedResponse<Host>> listHosts(
            @PathVariable UUID userId,
            HostListQuery query,
            @RequestParam(defaultValue = "${app.pagination.default-page}") int page,
            @RequestParam(defaultValue = "${app.pagination.default-size}") int size,
            HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.OK).body(hostService.listHosts(
                userId, query, page, size, request));
    }
}
