package com.techindna.springbootjwttemplate.dto;

import java.util.List;

public record PaginatedResponse<T>(List<T> data, Meta meta) {}
