package com.whu.graduation.taskincentive.controller;

import com.whu.graduation.taskincentive.dao.entity.TaskStock;
import com.whu.graduation.taskincentive.service.TaskStockService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TaskStockController.class)
@AutoConfigureMockMvc(addFilters = false)
public class TaskStockControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TaskStockService taskStockService;

    @Test
    public void get_shouldReturnOk() throws Exception {
        when(taskStockService.getById(org.mockito.Mockito.anyLong())).thenReturn(Collections.singletonList(new TaskStock()));
        mockMvc.perform(get("/api/task-stock/1").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    @Test
    public void create_shouldReturnOk() throws Exception {
        when(taskStockService.save(org.mockito.Mockito.any(TaskStock.class))).thenReturn(true);
        String body = "{\"taskId\":1,\"availableStock\":100}";
        mockMvc.perform(post("/api/task-stock/create").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
    }
}
