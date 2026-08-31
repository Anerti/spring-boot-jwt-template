package com.techindna.springbootjwttemplate.service;

import com.techindna.springbootjwttemplate.dto.HostListQuery;
import com.techindna.springbootjwttemplate.dto.Meta;
import com.techindna.springbootjwttemplate.dto.PaginatedResponse;
import com.techindna.springbootjwttemplate.entity.Host;
import com.techindna.springbootjwttemplate.mapper.HostMapper;
import com.techindna.springbootjwttemplate.repository.HostRepository;
import com.techindna.springbootjwttemplate.repository.model.JHost;
import com.techindna.springbootjwttemplate.validator.DataValidator;
import jakarta.servlet.http.HttpServletRequest;
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
public class HostService {

    private final HostRepository hostRepository;
    private final HostMapper hostMapper;
    private final DataValidator dataValidator;
    private final ABACRulesService abacRulesService;

    @Value("${app.pagination.default-page:1}")
    private int defaultPage;

    @Value("${app.pagination.default-size:20}")
    private int defaultSize;

    @Transactional(readOnly = true)
    public PaginatedResponse<Host> listHosts(
            UUID userId,
            HostListQuery query,
            int page,
            int size,
            HttpServletRequest request) {

        abacRulesService.grantAccessFor(userId, request);
        dataValidator.validateIpAddress(query.ipAddress());

        if (page < 1) {
            page = defaultPage;
        }
        if (size < 1 || size > 100) {
            size = defaultSize;
        }

        String sortByLastSeenAt = query.sortByLastSeenAt() != null ? query.sortByLastSeenAt() : "desc";

        Sort sort = Sort.by(
                "asc".equalsIgnoreCase(sortByLastSeenAt) ? Sort.Direction.ASC : Sort.Direction.DESC,
                "lastSeenAt");
        Pageable pageable = PageRequest.of(page - 1, size, sort);

        Page<JHost> resultPage = hostRepository.search(userId, query.ipAddress(), query.status(), pageable);
        List<Host> data = resultPage.getContent().stream().map(hostMapper::toDomain).toList();

        return new PaginatedResponse<>(data.isEmpty() ? null : data, new Meta(page, size, resultPage.getTotalElements()));
    }
}
