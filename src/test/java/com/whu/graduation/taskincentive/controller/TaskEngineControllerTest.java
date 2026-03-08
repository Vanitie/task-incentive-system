package com.whu.graduation.taskincentive.controller;

import com.whu.graduation.taskincentive.controller.TaskEngineController.ProcessEventRequest;
import com.whu.graduation.taskincentive.engine.TaskEngine;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.concurrent.ExecutorService;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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

    @MockBean(name = "dbWriteExecutor")
    private ExecutorService executorService;

    @Test
    public void processEventAsync_shouldAccept() throws Exception {
        String body = "{\"messageId\":\"m1\",\"userId\":1,\"eventType\":\"USER_LEARN\",\"value\":1}";
        mockMvc.perform(post("/api/engine/process-event-async").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isAccepted());
    }

    @Test
    public void processEventSync_shouldOk() throws Exception {
        String body = "{\"messageId\":\"m2\",\"userId\":1,\"eventType\":\"USER_LEARN\",\"value\":1}";
        mockMvc.perform(post("/api/engine/process-event-sync").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
    }
}
