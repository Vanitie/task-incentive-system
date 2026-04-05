package com.whu.graduation.taskincentive.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.whu.graduation.taskincentive.dao.entity.User;
import com.whu.graduation.taskincentive.dao.entity.UserActionLog;
import com.whu.graduation.taskincentive.dao.mapper.UserActionLogMapper;
import com.whu.graduation.taskincentive.dao.mapper.UserMapper;
import com.whu.graduation.taskincentive.testutil.ReflectionTestUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserActionLogServiceImplTest {

    private UserActionLogMapper userActionLogMapper;
    private UserMapper userMapper;
    private UserActionLogServiceImpl service;

    @BeforeEach
    void setUp() throws Exception {
        userActionLogMapper = mock(UserActionLogMapper.class);
        userMapper = mock(UserMapper.class);
        service = new UserActionLogServiceImpl(userActionLogMapper, userMapper);

        ReflectionTestUtils.setFieldRecursively(service, "baseMapper", userActionLogMapper);
    }

    @Test
    void queryByConditions_shouldReturnDirectlyWhenNoRecords() {
        Page<UserActionLog> page = new Page<>(1, 10);
        Page<UserActionLog> empty = new Page<>(1, 10);
        empty.setRecords(List.of());
        when(userActionLogMapper.selectPage(eq(page), any(QueryWrapper.class))).thenReturn(empty);

        Page<UserActionLog> out = service.queryByConditions(page, 1L, "LOGIN", "2026-01-01", "2026-01-02");

        assertEquals(0, out.getRecords().size());
        verify(userMapper, never()).selectBatchIds(any());
    }

    @Test
    void queryByConditions_shouldEnrichUserNamesForReturnedRecords() {
        Page<UserActionLog> page = new Page<>(1, 10);
        UserActionLog r1 = new UserActionLog();
        r1.setUserId(10L);
        UserActionLog r2 = new UserActionLog();
        r2.setUserId(null);
        UserActionLog r3 = new UserActionLog();
        r3.setUserId(20L);

        Page<UserActionLog> recordsPage = new Page<>(1, 10);
        recordsPage.setRecords(List.of(r1, r2, r3));

        when(userActionLogMapper.selectPage(eq(page), any(QueryWrapper.class))).thenReturn(recordsPage);
        when(userMapper.selectBatchIds(any(Set.class))).thenReturn(List.of(
                User.builder().id(10L).username("alice").build(),
                User.builder().id(20L).username("bob").build()
        ));

        Page<UserActionLog> out = service.queryByConditions(page, null, "", "", "");

        assertEquals("alice", out.getRecords().get(0).getUserName());
        assertNull(out.getRecords().get(1).getUserName());
        assertEquals("bob", out.getRecords().get(2).getUserName());
        verify(userMapper).selectBatchIds(any(Set.class));
    }

    @Test
    void selectByUserId_shouldDelegateToMapper() {
        when(userActionLogMapper.selectByUserId(9L)).thenReturn(List.of(new UserActionLog()));

        List<UserActionLog> out = service.selectByUserId(9L);

        assertEquals(1, out.size());
        verify(userActionLogMapper).selectByUserId(9L);
    }
}

