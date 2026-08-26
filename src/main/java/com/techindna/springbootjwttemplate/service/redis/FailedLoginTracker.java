package com.techindna.springbootjwttemplate.service.redis;

import java.time.Duration;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class FailedLoginTracker {

    private static final Duration TTL = Duration.ofMinutes(5);
    private static final String KEY_PREFIX = "failed_logins:";

    private final StringRedisTemplate redis;

    public int increment(UUID userId) {
        String key = KEY_PREFIX + userId.toString();
        Long count = redis.opsForValue().increment(key);
        if (count != null && count == 1) {
            redis.expire(key, TTL);
        }
        return count != null ? count.intValue() : 0;
    }
}
