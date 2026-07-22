package com.techindna.springbootjwttemplate.exception;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ErrorBody {

    private int status;

    private String error;

    private String message;

    @Builder.Default
    private Instant timestamp = Instant.now();
}
