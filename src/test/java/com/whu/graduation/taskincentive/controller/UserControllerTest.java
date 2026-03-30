package com.whu.graduation.taskincentive.controller;

import com.whu.graduation.taskincentive.dao.entity.User;
import com.whu.graduation.taskincentive.security.JwtUtil;
import com.whu.graduation.taskincentive.service.UserService;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import com.whu.graduation.taskincentive.config.SecurityConfig;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserController.class)
@AutoConfigureMockMvc
@Import(SecurityConfig.class)
public class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserService userService;

    @MockBean
    private JwtUtil jwtUtil;

    @Test
    public void list_withoutToken_shouldReturnUnauthorized() throws Exception {
        mockMvc.perform(get("/api/user/list").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    public void list_asUser_shouldReturnForbidden() throws Exception {
        mockUserToken("t-user", "u1", List.of("ROLE_USER"));
        mockMvc.perform(get("/api/user/list").contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer t-user"))
                .andExpect(status().isForbidden());
    }

    @Test
    public void list_asAdmin_shouldReturnOk() throws Exception {
        when(userService.selectPage(org.mockito.Mockito.any())).thenReturn(new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>());
        mockUserToken("t-admin", "admin", List.of("ROLE_ADMIN"));
        mockMvc.perform(get("/api/user/list").contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer t-admin"))
                .andExpect(status().isOk());
    }

    @Test
    public void create_asUser_shouldReturnForbidden() throws Exception {
        mockUserToken("t-user", "u1", List.of("ROLE_USER"));
        String body = "{\"username\":\"u2\"}";
        mockMvc.perform(post("/api/user/create").contentType(MediaType.APPLICATION_JSON).content(body)
                        .header("Authorization", "Bearer t-user"))
                .andExpect(status().isForbidden());
    }

    @Test
    public void create_asAdmin_shouldReturnOk() throws Exception {
        when(userService.save(org.mockito.Mockito.any(User.class))).thenReturn(true);
        mockUserToken("t-admin", "admin", List.of("ROLE_ADMIN"));
        String body = "{\"username\":\"u2\"}";
        mockMvc.perform(post("/api/user/create").contentType(MediaType.APPLICATION_JSON).content(body)
                        .header("Authorization", "Bearer t-admin"))
                .andExpect(status().isOk());
    }

    @Test
    public void getById_asUser_shouldReturnForbidden() throws Exception {
        mockUserToken("t-user", "u1", List.of("ROLE_USER"));
        mockMvc.perform(get("/api/user/1001").contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer t-user"))
                .andExpect(status().isForbidden());
    }

    @Test
    public void update_self_asUser_shouldReturnOk() throws Exception {
        User login = new User();
        login.setId(1001L);
        login.setUsername("u1");

        when(userService.selectByUsername("u1")).thenReturn(login);
        when(userService.update(any(User.class))).thenReturn(true);

        mockUserToken("t-user", "u1", List.of("ROLE_USER"));
        String body = "{\"id\":1001,\"username\":\"u1-new\",\"roles\":\"ROLE_ADMIN\",\"pointBalance\":9999}";

        mockMvc.perform(put("/api/user/update").contentType(MediaType.APPLICATION_JSON).content(body)
                        .header("Authorization", "Bearer t-user"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    public void update_other_asUser_shouldReturnBusinessForbidden() throws Exception {
        User login = new User();
        login.setId(1001L);
        login.setUsername("u1");
        when(userService.selectByUsername("u1")).thenReturn(login);

        mockUserToken("t-user", "u1", List.of("ROLE_USER"));
        String body = "{\"id\":1002,\"username\":\"u2\"}";

        mockMvc.perform(put("/api/user/update").contentType(MediaType.APPLICATION_JSON).content(body)
                        .header("Authorization", "Bearer t-user"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    public void updatePoints_missingParams_shouldReturnBusinessBadRequest() throws Exception {
        mockUserToken("t-admin", "admin", List.of("ROLE_ADMIN"));
        mockMvc.perform(post("/api/user/points").contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer t-admin"))
                .andExpect(status().isBadRequest());
    }

    @Test
    public void countTaskParticipants_asAdmin_shouldReturnOk() throws Exception {
        when(userService.getTaskReceiveUserCountLast7Days()).thenReturn(List.of(1L, 2L, 3L, 4L, 5L, 6L, 7L));
        mockUserToken("t-admin", "admin", List.of("ROLE_ADMIN"));

        mockMvc.perform(get("/api/user/count/task-participants-7days").contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer t-admin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    private void mockUserToken(String token, String username, List<String> roles) {
        Claims claims = org.mockito.Mockito.mock(Claims.class);
        when(jwtUtil.parseToken(token)).thenReturn(claims);
        when(claims.getSubject()).thenReturn(username);
        when(claims.get("roles")).thenReturn(roles);
    }
}
