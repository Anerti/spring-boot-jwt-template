package com.techindna.springbootjwttemplate.service;

import java.time.Duration;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class VerificationCodeStore {

    private static final Duration TTL = Duration.ofMinutes(15);
    private static final String KEY_PREFIX = "verification:";

    private final StringRedisTemplate redis;

    public void save(String email, String code) {
        redis.opsForValue().set(KEY_PREFIX + email, code, TTL);
    }

    public Optional<String> get(String email) {
        return Optional.ofNullable(redis.opsForValue().get(KEY_PREFIX + email));
    }

    public void delete(String email) {
        redis.delete(KEY_PREFIX + email);
    }
}
