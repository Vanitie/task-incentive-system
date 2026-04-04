package com.whu.graduation.taskincentive.controller;

import com.whu.graduation.taskincentive.engine.TaskEngine;
import com.whu.graduation.taskincentive.event.UserEvent;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TaskEngineController.class)
@AutoConfigureMockMvc(addFilters = false)
public class TaskEngineControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TaskEngine taskEngine;

    @MockBean
    private RedisTemplate<String, String> redisTemplate;

    @MockBean(name = "taskEngineEventExecutor")
    private ExecutorService executorService;

    @MockBean
    private ValueOperations<String, String> valueOperations;

    @Test
    public void processEventAsync_shouldAccept() throws Exception {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Long.class), any(TimeUnit.class))).thenReturn(true);
        doAnswer(invocation -> {
            Runnable task = invocation.getArgument(0);
            task.run();
            return CompletableFuture.completedFuture(null);
        }).when(executorService).submit(any(Runnable.class));

        String body = "{\"messageId\":\"m1\",\"userId\":1,\"eventType\":\"USER_LEARN\",\"value\":1}";
        mockMvc.perform(post("/api/engine/process-event-async").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isAccepted());

        verify(taskEngine).processEvent(any(UserEvent.class));
    }

    @Test
    public void processEventSync_shouldOk() throws Exception {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Long.class), any(TimeUnit.class))).thenReturn(true);

        String body = "{\"messageId\":\"m2\",\"userId\":1,\"eventType\":\"USER_LEARN\",\"value\":1}";
        mockMvc.perform(post("/api/engine/process-event-sync").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());

        verify(taskEngine).processEvent(any(UserEvent.class));
    }

    @Test
    public void processEventAsync_shouldReturnDuplicate_whenMessageIdRepeated() throws Exception {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Long.class), any(TimeUnit.class))).thenReturn(false);

        String body = "{\"messageId\":\"dup-1\",\"userId\":1,\"eventType\":\"USER_LEARN\"}";
        mockMvc.perform(post("/api/engine/process-event-async").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.status").value("duplicate"));

        verify(executorService, never()).submit(any(Runnable.class));
        verify(taskEngine, never()).processEvent(any(UserEvent.class));
    }

    @Test
    public void processEventAsync_shouldReturn503_whenExecutorRejected() throws Exception {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Long.class), any(TimeUnit.class))).thenReturn(true);
        when(executorService.submit(any(Runnable.class))).thenThrow(new RejectedExecutionException("full"));

        String body = "{\"messageId\":\"m-overload\",\"userId\":1,\"eventType\":\"USER_LEARN\"}";
        mockMvc.perform(post("/api/engine/process-event-async").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value(503));
    }

    @Test
    public void processEventSync_shouldNormalizeLongRequestId() throws Exception {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Long.class), any(TimeUnit.class))).thenReturn(true);

        String longRequestId = "r".repeat(80);
        String body = "{\"messageId\":\"m3\",\"requestId\":\"" + longRequestId + "\",\"userId\":1,\"eventType\":\"USER_LEARN\"}";
        mockMvc.perform(post("/api/engine/process-event-sync").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("processed"));

        ArgumentCaptor<UserEvent> captor = ArgumentCaptor.forClass(UserEvent.class);
        verify(taskEngine).processEvent(captor.capture());
        String requestId = captor.getValue().getRequestId();
        assertTrue(requestId.startsWith("reqh-"));
        assertEquals(37, requestId.length());
    }

    @Test
    public void processEventAsync_shouldReturnBadRequest_whenMissingUserId() throws Exception {
        String body = "{\"messageId\":\"m4\",\"eventType\":\"USER_LEARN\"}";
        mockMvc.perform(post("/api/engine/process-event-async").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());

        verify(taskEngine, never()).processEvent(any(UserEvent.class));
    }

    @Test
    public void processEventAsync_shouldReturnBadRequest_whenBlankEventType() throws Exception {
        String body = "{\"messageId\":\"m4-blank\",\"userId\":1,\"eventType\":\"   \"}";
        mockMvc.perform(post("/api/engine/process-event-async").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());

        verify(taskEngine, never()).processEvent(any(UserEvent.class));
    }

    @Test
    public void processEventSync_shouldReturnDuplicate_whenMessageIdRepeated() throws Exception {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Long.class), any(TimeUnit.class))).thenReturn(false);

        String body = "{\"messageId\":\"dup-sync-1\",\"userId\":1,\"eventType\":\"USER_LEARN\"}";
        mockMvc.perform(post("/api/engine/process-event-sync").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.status").value("duplicate"));

        verify(taskEngine, never()).processEvent(any(UserEvent.class));
    }

    @Test
    public void processEventSync_shouldReturnBusinessError_whenEngineThrows() throws Exception {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Long.class), any(TimeUnit.class))).thenReturn(true);
        org.mockito.Mockito.doThrow(new RuntimeException("boom")).when(taskEngine).processEvent(any(UserEvent.class));

        String body = "{\"messageId\":\"m5\",\"userId\":1,\"eventType\":\"USER_LEARN\"}";
        mockMvc.perform(post("/api/engine/process-event-sync").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.msg").value("boom"));
    }

    @Test
    public void processEventAsync_shouldAccept_whenDedupRedisThrows() throws Exception {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Long.class), any(TimeUnit.class)))
                .thenThrow(new RuntimeException("redis down"));
        doAnswer(invocation -> {
            Runnable task = invocation.getArgument(0);
            task.run();
            return CompletableFuture.completedFuture(null);
        }).when(executorService).submit(any(Runnable.class));

        String body = "{\"messageId\":\"m-redis-fail\",\"userId\":1,\"eventType\":\"USER_LEARN\"}";
        mockMvc.perform(post("/api/engine/process-event-async").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isAccepted());

        verify(taskEngine, times(1)).processEvent(any(UserEvent.class));
    }
}
