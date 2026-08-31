package com.techindna.springbootjwttemplate.validator;

import com.techindna.springbootjwttemplate.entity.enums.HostStatus;
import com.techindna.springbootjwttemplate.exception.http.UnprocessableContentException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class HostValidator {

    public void validateListFilters(int page, int size) {
        if (size < 1 || size > 100) {
            throw new UnprocessableContentException("Page size must be between 1 and 100");
        }
    }
}
