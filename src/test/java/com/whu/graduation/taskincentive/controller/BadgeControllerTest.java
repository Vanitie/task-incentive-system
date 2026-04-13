package com.whu.graduation.taskincentive.controller;

import com.whu.graduation.taskincentive.dao.entity.Badge;
import com.whu.graduation.taskincentive.service.BadgeImageStorageService;
import com.whu.graduation.taskincentive.service.BadgeService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(BadgeController.class)
@AutoConfigureMockMvc(addFilters = false)
public class BadgeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private BadgeService badgeService;

    @MockBean
    private BadgeImageStorageService badgeImageStorageService;

    @Test
    public void list_shouldReturnOk() throws Exception {
        when(badgeService.selectPage(org.mockito.Mockito.any())).thenReturn(new com.baomidou.mybatisplus.extension.plugins.pagination.Page<Badge>());
        mockMvc.perform(get("/api/badge/list").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    @Test
    public void create_shouldReturnOk() throws Exception {
        when(badgeService.save(org.mockito.Mockito.any(Badge.class))).thenReturn(true);
        String body = "{\"name\":\"b1\"}";
        mockMvc.perform(post("/api/badge/create").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
    }

    @Test
    public void uploadImage_shouldReturnOk() throws Exception {
        when(badgeImageStorageService.store(org.mockito.Mockito.any()))
                .thenReturn(new BadgeImageStorageService.StoredBadgeImage("a.png", null, MediaType.IMAGE_PNG));

        MockMultipartFile file = new MockMultipartFile("file", "a.png", "image/png", "abc".getBytes());
        mockMvc.perform(multipart("/api/badge/upload-image").file(file))
                .andExpect(status().isOk());
    }
}
