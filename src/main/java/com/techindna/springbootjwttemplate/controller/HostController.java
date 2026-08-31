package com.techindna.springbootjwttemplate.controller;

import com.techindna.springbootjwttemplate.dto.PaginatedResponse;
import com.techindna.springbootjwttemplate.entity.Host;
import com.techindna.springbootjwttemplate.entity.enums.HostStatus;
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
            @RequestParam(required = false) String ipAddress,
            @RequestParam(required = false) HostStatus status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "desc") String sortByLastSeenAt,
            HttpServletRequest request) {
        PaginatedResponse<Host> body = hostService.listHosts(
                userId, ipAddress, status, page, size, sortByLastSeenAt, request);
        return ResponseEntity.status(HttpStatus.OK).body(body);
    }
}
