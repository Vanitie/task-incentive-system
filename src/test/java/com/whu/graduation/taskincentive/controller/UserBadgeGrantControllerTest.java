package com.whu.graduation.taskincentive.controller;

import com.whu.graduation.taskincentive.service.UserBadgeService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserBadgeController.class)
@AutoConfigureMockMvc(addFilters = false)
public class UserBadgeGrantControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserBadgeService userBadgeService;

    @Test
    public void grant_shouldReturnOk() throws Exception {
        when(userBadgeService.grantBadge(org.mockito.Mockito.anyLong(), org.mockito.Mockito.anyInt())).thenReturn(true);
        mockMvc.perform(post("/api/user-badge/grant?userId=1&badgeCode=101").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }
}
