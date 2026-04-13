package com.whu.graduation.taskincentive.controller;

import com.whu.graduation.taskincentive.dao.entity.User;
import com.whu.graduation.taskincentive.dao.mapper.AdminOperationLogMapper;
import com.whu.graduation.taskincentive.dao.mapper.UserActionLogMapper;
import com.whu.graduation.taskincentive.dao.mapper.UserMapper;
import com.whu.graduation.taskincentive.dao.mapper.UserTaskInstanceMapper;
import com.whu.graduation.taskincentive.security.JwtUtil;
import com.whu.graduation.taskincentive.dto.ApiResponse;
import com.whu.graduation.taskincentive.dto.PageResult;
import com.whu.graduation.taskincentive.service.UserRewardRecordService;
import com.whu.graduation.taskincentive.service.UserService;
import com.whu.graduation.taskincentive.testutil.ReflectionTestUtils;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import com.whu.graduation.taskincentive.config.SecurityConfig;

import java.util.List;
import java.util.Date;
import java.lang.reflect.Method;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doReturn;
import static org.junit.jupiter.api.Assertions.*;
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

    @MockBean
    private UserRewardRecordService userRewardRecordService;

    @MockBean
    private UserMapper userMapper;

    @MockBean
    private UserTaskInstanceMapper userTaskInstanceMapper;

    @MockBean
    private UserActionLogMapper userActionLogMapper;

    @MockBean
    private com.whu.graduation.taskincentive.service.UserActionLogService userActionLogService;

    @MockBean
    private AdminOperationLogMapper adminOperationLogMapper;

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
    public void list_invalidPageBoundary_shouldReturnBusinessBadRequest() throws Exception {
        mockUserToken("t-admin", "admin", List.of("ROLE_ADMIN"));
        mockMvc.perform(get("/api/user/list").param("page", "0").param("size", "20")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer t-admin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    public void list_invalidSizeBoundary_shouldReturnBusinessBadRequest() throws Exception {
        mockUserToken("t-admin", "admin", List.of("ROLE_ADMIN"));
        mockMvc.perform(get("/api/user/list").param("page", "1").param("size", "101")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer t-admin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));
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
    public void getById_asAdmin_shouldReturnOk() throws Exception {
        User u = new User();
        u.setId(1001L);
        u.setUsername("u1");
        when(userService.getById(1001L)).thenReturn(u);
        mockUserToken("t-admin", "admin", List.of("ROLE_ADMIN"));

        mockMvc.perform(get("/api/user/1001").contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer t-admin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(1001L))
                .andExpect(jsonPath("$.data.username").value("u1"));
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
    public void update_missingId_shouldReturnBusinessBadRequest() throws Exception {
        mockUserToken("t-admin", "admin", List.of("ROLE_ADMIN"));
        String body = "{\"username\":\"u2\"}";

        mockMvc.perform(put("/api/user/update").contentType(MediaType.APPLICATION_JSON).content(body)
                        .header("Authorization", "Bearer t-admin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    public void update_asAdmin_shouldReturnOk() throws Exception {
        when(userService.update(any(User.class))).thenReturn(true);
        mockUserToken("t-admin", "admin", List.of("ROLE_ADMIN"));
        String body = "{\"id\":1002,\"username\":\"u2\",\"roles\":\"ROLE_USER\",\"pointBalance\":66}";

        mockMvc.perform(put("/api/user/update").contentType(MediaType.APPLICATION_JSON).content(body)
                        .header("Authorization", "Bearer t-admin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        verify(userService).update(any(User.class));
    }

    @Test
    public void updatePoints_missingParams_shouldReturnBusinessBadRequest() throws Exception {
        mockUserToken("t-admin", "admin", List.of("ROLE_ADMIN"));
        mockMvc.perform(post("/api/user/points").contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer t-admin"))
                .andExpect(status().isInternalServerError());
    }

    @Test
    public void updatePoints_valid_shouldReturnOk() throws Exception {
        mockUserToken("t-admin", "admin", List.of("ROLE_ADMIN"));
        when(userService.updateUserPoints(anyLong(), anyInt())).thenReturn(true);

        mockMvc.perform(post("/api/user/points")
                        .param("userId", "1001")
                        .param("points", "20")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer t-admin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").value(true));

        verify(userService).updateUserPoints(1001L, 20);
    }

    @Test
    public void updatePoints_whenServiceReturnsFalse_shouldNotWriteRewardRecord() throws Exception {
        mockUserToken("t-admin", "admin", List.of("ROLE_ADMIN"));
        when(userService.updateUserPoints(1001L, -20)).thenReturn(false);

        mockMvc.perform(post("/api/user/points")
                        .param("userId", "1001")
                        .param("points", "-20")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer t-admin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").value(false));

        verify(userRewardRecordService, never()).save(any());
    }

    @Test
    public void countTaskParticipants_asAdmin_shouldReturnOk() throws Exception {
        when(userService.getTaskReceiveUserCountLast7Days()).thenReturn(List.of(1L, 2L, 3L, 4L, 5L, 6L, 7L));
        mockUserToken("t-admin", "admin", List.of("ROLE_ADMIN"));

        mockMvc.perform(get("/api/user/count/today").contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer t-admin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    public void countTotalUsers_asAdmin_shouldReturnOk() throws Exception {
        when(userService.getUserCountLast7Days()).thenReturn(List.of(0L, 0L, 0L, 0L, 0L, 0L, 3L));
        when(userService.countAllUsers()).thenReturn(20L);
        mockUserToken("t-admin", "admin", List.of("ROLE_ADMIN"));

        mockMvc.perform(get("/api/user/count/total").contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer t-admin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.value").value(20));
    }

    @Test
    public void countActive7Days_asAdmin_shouldReturnOk() throws Exception {
        when(userService.getActiveUserCountLast7Days()).thenReturn(List.of(1L, 2L, 3L, 4L, 5L, 6L, 7L));
        mockUserToken("t-admin", "admin", List.of("ROLE_ADMIN"));

        mockMvc.perform(get("/api/user/count/active7days").contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer t-admin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.value").value(7));
    }

    @Test
    public void delete_asAdmin_shouldReturnOk() throws Exception {
        when(userService.deleteById(1003L)).thenReturn(true);
        mockUserToken("t-admin", "admin", List.of("ROLE_ADMIN"));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/api/user/1003")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer t-admin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").value(true));
        verify(userService).deleteById(eq(1003L));
    }

    @Test
    public void getById_asAdmin_shouldReturnNullData_whenUserMissing() throws Exception {
        when(userService.getById(1005L)).thenReturn(null);
        mockUserToken("t-admin", "admin", List.of("ROLE_ADMIN"));

        mockMvc.perform(get("/api/user/1005").contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer t-admin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    public void privateHelpers_shouldCoverComputePercentLastOrZeroAndMaskSensitive() throws Exception {
        UserController plain = new UserController();
        ReflectionTestUtils.setFieldRecursively(plain, "userService", userService);
        ReflectionTestUtils.setFieldRecursively(plain, "userRewardRecordService", userRewardRecordService);

        Method computePercent = UserController.class.getDeclaredMethod("computePercent", List.class);
        computePercent.setAccessible(true);
        assertEquals("+0%", computePercent.invoke(plain, (Object) null));
        assertEquals("+0%", computePercent.invoke(plain, List.of(8L)));
        assertEquals("+100%", computePercent.invoke(plain, List.of(0L, 5L)));
        assertEquals("-50%", computePercent.invoke(plain, List.of(10L, 5L)));

        Method lastOrZero = UserController.class.getDeclaredMethod("lastOrZero", List.class);
        lastOrZero.setAccessible(true);
        assertEquals(0L, ((Long) lastOrZero.invoke(plain, (Object) null)).longValue());
        assertEquals(0L, ((Long) lastOrZero.invoke(plain, List.of())).longValue());
        assertEquals(7L, ((Long) lastOrZero.invoke(plain, List.of(1L, 7L))).longValue());

        Method maskSensitive = UserController.class.getDeclaredMethod("maskSensitive", User.class);
        maskSensitive.setAccessible(true);
        assertNull(maskSensitive.invoke(plain, new Object[]{null}));

        User src = new User();
        src.setId(1L);
        src.setUsername("u");
        src.setRoles("ROLE_USER");
        src.setPointBalance(9);
        src.setCreateTime(new Date());
        src.setUpdateTime(new Date());
        src.setPassword("secret");

        User masked = (User) maskSensitive.invoke(plain, src);
        assertNotNull(masked);
        assertEquals(src.getId(), masked.getId());
        assertEquals(src.getUsername(), masked.getUsername());
        assertEquals(src.getRoles(), masked.getRoles());
        assertEquals(src.getPointBalance(), masked.getPointBalance());
        assertNull(masked.getPassword());
    }

    @Test
    public void unit_update_shouldReturnUnauthorized_whenAuthenticationMissingForNonAdminFlow() {
        UserController plain = new UserController();
        ReflectionTestUtils.setFieldRecursively(plain, "userService", userService);
        ReflectionTestUtils.setFieldRecursively(plain, "userRewardRecordService", userRewardRecordService);

        User patch = new User();
        patch.setId(1001L);
        patch.setUsername("u1");

        ApiResponse<Boolean> out = plain.update(patch, null);
        assertEquals(401, out.getCode());
    }

    @Test
    public void unit_update_shouldReturnForbidden_whenLoginUserNotFound() {
        UserController plain = new UserController();
        ReflectionTestUtils.setFieldRecursively(plain, "userService", userService);
        ReflectionTestUtils.setFieldRecursively(plain, "userRewardRecordService", userRewardRecordService);

        Authentication auth = org.mockito.Mockito.mock(Authentication.class);
        doReturn(List.of(new SimpleGrantedAuthority("ROLE_USER"))).when(auth).getAuthorities();
        when(auth.getName()).thenReturn("u-missing");
        when(userService.selectByUsername("u-missing")).thenReturn(null);

        User patch = new User();
        patch.setId(1001L);
        patch.setUsername("u1");

        ApiResponse<Boolean> out = plain.update(patch, auth);
        assertEquals(403, out.getCode());
    }

    @Test
    public void unit_listAll_shouldHandleNullRecords_andInvalidSizeLowerBound() {
        UserController plain = new UserController();
        ReflectionTestUtils.setFieldRecursively(plain, "userService", userService);
        ReflectionTestUtils.setFieldRecursively(plain, "userRewardRecordService", userRewardRecordService);

        ApiResponse<PageResult<User>> invalid = plain.listAll(1, 0);
        assertEquals(400, invalid.getCode());

        com.baomidou.mybatisplus.extension.plugins.pagination.Page<User> p = new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(1, 20);
        p.setRecords(null);
        p.setTotal(0);
        when(userService.selectPage(any())).thenReturn(p);

        ApiResponse<PageResult<User>> ok = plain.listAll(1, 20);
        assertEquals(0, ok.getCode());
        assertNotNull(ok.getData());
        assertNotNull(ok.getData().getItems());
        assertEquals(0, ok.getData().getItems().size());
    }

    @Test
    public void unit_updatePoints_shouldReturnBadRequest_whenArgumentsNull() {
        UserController plain = new UserController();
        ReflectionTestUtils.setFieldRecursively(plain, "userService", userService);
        ReflectionTestUtils.setFieldRecursively(plain, "userRewardRecordService", userRewardRecordService);

        ApiResponse<Boolean> nullUser = plain.updatePoints(null, 1);
        assertEquals(400, nullUser.getCode());

        ApiResponse<Boolean> nullPoints = plain.updatePoints(1001L, null);
        assertEquals(400, nullPoints.getCode());
    }

    private void mockUserToken(String token, String username, List<String> roles) {
        Claims claims = org.mockito.Mockito.mock(Claims.class);
        when(jwtUtil.parseToken(token)).thenReturn(claims);
        when(claims.getSubject()).thenReturn(username);
        when(claims.get("roles")).thenReturn(roles);
    }
}
