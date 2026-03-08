package com.whu.graduation.taskincentive.controller;

import com.whu.graduation.taskincentive.dao.entity.TaskConfig;
import com.whu.graduation.taskincentive.service.TaskConfigService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TaskConfigController.class)
@AutoConfigureMockMvc(addFilters = false)
public class TaskConfigControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TaskConfigService taskConfigService;

    @Test
    public void list_shouldReturnOk() throws Exception {
        when(taskConfigService.selectPage(org.mockito.Mockito.any())).thenReturn(new com.baomidou.mybatisplus.extension.plugins.pagination.Page<TaskConfig>());
        mockMvc.perform(get("/api/task-config/list").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    @Test
    public void create_shouldReturnOk() throws Exception {
        when(taskConfigService.save(org.mockito.Mockito.any(TaskConfig.class))).thenReturn(true);
        String body = "{\"name\":\"t1\"}";
        mockMvc.perform(post("/api/task-config/create").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
    }
}
