package com.whu.graduation.taskincentive.controller;

import com.whu.graduation.taskincentive.constant.CacheKeys;
import com.whu.graduation.taskincentive.dto.ApiResponse;
import com.whu.graduation.taskincentive.engine.TaskEngine;
import com.whu.graduation.taskincentive.event.UserEvent;
import io.swagger.v3.oas.annotations.Operation;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.*;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.validation.annotation.Validated;
import org.springframework.security.access.prepost.PreAuthorize;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

/**
 * TaskEngine 对外入口：负责接收事件、做幂等判断并异步委托给引擎处理。
 */
@Slf4j
@RestController
@Validated
public class TaskEngineController {

    @Autowired
    private TaskEngine taskEngine;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @Autowired
    @Qualifier("dbWriteExecutor")
    private ExecutorService executorService;

    /**
     * 异步处理单条事件（对外 API）
     * 请求示例 body:
     * {
     *   "messageId": "uuid-123",
     *   "userId": 1001,
     *   "eventType": "USER_LEARN",
     *   "value": 1,
     *   "time": "2026-03-08T12:00:00"
     * }
     */
    @Operation(summary = "异步处理用户事件（非阻塞）")
    @PostMapping("/api/engine/process-event-async")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public org.springframework.http.ResponseEntity<ApiResponse<?>> processEvent(@Valid @RequestBody ProcessEventRequest req) {
        if (isDuplicateMessage(req.getMessageId())) {
            return org.springframework.http.ResponseEntity.ok(ApiResponse.success(Collections.singletonMap("status", "duplicate")));
        }

        UserEvent event = new UserEvent();
        event.setUserId(req.getUserId());
        event.setEventType(req.getEventType());
        event.setValue(req.getValue());
        if (req.getTime() != null) {
            event.setTime(req.getTime());
        } else {
            event.setTime(LocalDateTime.now());
        }
        event.setRequestId(resolveRequestId(req));
        event.setEventId(req.getEventId());
        event.setDeviceId(req.getDeviceId());
        event.setIp(req.getIp());
        event.setChannel(req.getChannel());
        event.setExt(req.getExt());

        try {
            try {
                executorService.submit(() -> {
                    try {
                        taskEngine.processEvent(event);
                    } catch (Exception ex) {
                        log.error("taskEngine failed to process event userId={} eventType={}, err= {}", event.getUserId(), event.getEventType(), ex.getMessage(), ex);
                    }
                });
            } catch (RejectedExecutionException rex) {
                log.error("executor rejected taskEngine job", rex);
                return org.springframework.http.ResponseEntity.status(503).body(ApiResponse.error(503, "executor overloaded"));
            }
        } catch (Exception e) {
            log.error("failed to submit taskEngine job", e);
            return org.springframework.http.ResponseEntity.status(500).body(ApiResponse.error(500, "submit failed"));
        }

        return org.springframework.http.ResponseEntity.accepted().body(ApiResponse.success());
    }

    /**
     * 同步处理
     */
    @Operation(summary = "同步处理用户事件（阻塞，立即返回处理结果）")
    @PostMapping("/api/engine/process-event-sync")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public ApiResponse<?> processEventSync(@Valid @RequestBody ProcessEventRequest req) {
        if (isDuplicateMessage(req.getMessageId())) {
            return ApiResponse.success(Collections.singletonMap("status", "duplicate"));
        }

        UserEvent event = new UserEvent();
        event.setUserId(req.getUserId());
        event.setEventType(req.getEventType());
        event.setValue(req.getValue());
        event.setTime(req.getTime() == null ? LocalDateTime.now() : req.getTime());
        event.setRequestId(resolveRequestId(req));
        event.setEventId(req.getEventId());
        event.setDeviceId(req.getDeviceId());
        event.setIp(req.getIp());
        event.setChannel(req.getChannel());
        event.setExt(req.getExt());

        try {
            taskEngine.processEvent(event);
            return ApiResponse.success(Collections.singletonMap("status", "processed"));
        } catch (Exception e) {
            log.error("sync process failed", e);
            return ApiResponse.error(500, e.getMessage());
        }
    }

    private String resolveRequestId(ProcessEventRequest req) {
        String requestId = req == null ? null : req.getRequestId();
        if (requestId != null) {
            requestId = requestId.trim();
            if (!requestId.isEmpty()) {
                return requestId;
            }
        }
        return "req-" + UUID.randomUUID();
    }

    private boolean isDuplicateMessage(String messageId) {
        if (messageId == null || messageId.isEmpty()) {
            return false;
        }
        String dedupKey = CacheKeys.DEDUP_MSG_PREFIX + messageId;
        try {
            Boolean set = redisTemplate.opsForValue().setIfAbsent(dedupKey, "1", CacheKeys.DEFAULT_DEDUP_TTL_DAYS, TimeUnit.DAYS);
            if (Boolean.FALSE.equals(set)) {
                log.info("duplicate event ignored, messageId={}", messageId);
                return true;
            }
        } catch (Exception e) {
            // Redis 异常时降级继续，避免影响主链路处理
            log.warn("redis dedup check failed for messageId={}, err={}", messageId, e.getMessage());
        }
        return false;
    }

    @Data
    public static class ProcessEventRequest {
        private String messageId;

        /** 请求ID，用于幂等 */
        private String requestId;

        /** 事件ID */
        private String eventId;

        /** 设备ID */
        private String deviceId;

        /** IP */
        private String ip;

        /** 渠道 */
        private String channel;

        /** 扩展字段 */
        private java.util.Map<String, Object> ext;

        @NotNull(message = "userId required")
        private Long userId;

        @NotBlank(message = "eventType required")
        private String eventType;

        private Integer value;
        private LocalDateTime time;
    }
}
