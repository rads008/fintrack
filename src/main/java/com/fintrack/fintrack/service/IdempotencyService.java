package com.fintrack.fintrack.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.function.Supplier;

@Service
@RequiredArgsConstructor
public class IdempotencyService {

    private final RedisTemplate<String, Object> redisTemplate;

    private static final String PREFIX = "idem:";

    public Object get(String key) {
        return redisTemplate.opsForValue().get(PREFIX + key);
    }

    public void save(String key, Object value) {
        redisTemplate.opsForValue().set(
                PREFIX + key,
                value,
                Duration.ofHours(24) // TTL
        );
    }
    public <T> T execute(String key, Supplier<T> action) {
        if (key == null || key.isBlank()) {
            return action.get();
        }

        String prefixedKey = PREFIX + key;

        // Atomic check-and-set — only one thread wins
        Boolean isNew = redisTemplate.opsForValue()
                .setIfAbsent(prefixedKey, "PROCESSING", Duration.ofHours(24));

        if (Boolean.FALSE.equals(isNew)) {
            // Another request already executed or is executing
            Object cached = redisTemplate.opsForValue().get(prefixedKey);
            if (cached != null && !"PROCESSING".equals(cached)) {
                return (T) cached;
            }
            throw new RuntimeException("Duplicate request — result not yet ready, retry shortly");
        }

        // We won the lock — execute and store result
        try {
            T result = action.get();
            redisTemplate.opsForValue().set(prefixedKey, result, Duration.ofHours(24));
            return result;
        } catch (Exception e) {
            // Don't lock out the key if the action itself failed
            redisTemplate.delete(prefixedKey);
            throw e;
        }
    }
}