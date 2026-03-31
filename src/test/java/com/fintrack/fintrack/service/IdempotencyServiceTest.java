package com.fintrack.fintrack.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.mockito.Mockito.verify;
import static org.mockito.ArgumentMatchers.eq;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;
import static org.junit.jupiter.api.Assertions.assertThrows;

@ExtendWith(MockitoExtension.class)
class IdempotencyServiceTest {

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    @InjectMocks
    private IdempotencyService idempotencyService;

    @BeforeEach
    void setup() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }
    @Test
    void testExecute_firstCall_executesAndStores() {

        String key = "abc";

        when(valueOperations.setIfAbsent(anyString(), any(), any()))
                .thenReturn(true);

        String result = idempotencyService.execute(key, () -> "SUCCESS");

        assertEquals("SUCCESS", result);

        verify(valueOperations).set(
                eq("idem:" + key),
                eq("SUCCESS"),
                any()
        );
    }
    @Test
    void testExecute_returnsCachedResult() {

        String key = "abc";

        when(valueOperations.setIfAbsent(anyString(), any(), any()))
                .thenReturn(false);

        when(valueOperations.get("idem:" + key))
                .thenReturn("CACHED_RESULT");

        String result = idempotencyService.execute(key, () -> "NEW_RESULT");

        assertEquals("CACHED_RESULT", result);

        verify(valueOperations, never()).set(any(), eq("NEW_RESULT"), any());
    }
    @Test
    void testExecute_processingState_throwsException() {

        String key = "abc";

        when(valueOperations.setIfAbsent(anyString(), any(), any()))
                .thenReturn(false);

        when(valueOperations.get("idem:" + key))
                .thenReturn("PROCESSING");

        assertThrows(RuntimeException.class, () -> {
            idempotencyService.execute(key, () -> "RESULT");
        });
    }
    @Test
    void testExecute_failure_deletesKey() {

        String key = "abc";

        when(valueOperations.setIfAbsent(anyString(), any(), any()))
                .thenReturn(true);

        RuntimeException exception = new RuntimeException("Failure");

        assertThrows(RuntimeException.class, () -> {
            idempotencyService.execute(key, () -> {
                throw exception;
            });
        });

        verify(redisTemplate).delete("idem:" + key);
    }
}