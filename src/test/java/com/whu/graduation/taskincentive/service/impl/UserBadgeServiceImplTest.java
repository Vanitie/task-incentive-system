package com.whu.graduation.taskincentive.service.impl;

import com.whu.graduation.taskincentive.dao.entity.Badge;
import com.whu.graduation.taskincentive.dao.entity.UserBadge;
import com.whu.graduation.taskincentive.dao.mapper.BadgeMapper;
import com.whu.graduation.taskincentive.dao.mapper.UserBadgeMapper;
import com.whu.graduation.taskincentive.testutil.ReflectionTestUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserBadgeServiceImplTest {

    private UserBadgeMapper userBadgeMapper;
    private BadgeMapper badgeMapper;
    private UserBadgeServiceImpl service;

    @BeforeEach
    void setUp() throws Exception {
        userBadgeMapper = mock(UserBadgeMapper.class);
        badgeMapper = mock(BadgeMapper.class);
        service = new UserBadgeServiceImpl(userBadgeMapper, badgeMapper);

        ReflectionTestUtils.setFieldRecursively(service, "baseMapper", userBadgeMapper);
    }

    @Test
    void grantBadge_shouldReturnFalseWhenBadgeMissing() {
        when(badgeMapper.selectOne(any())).thenReturn(null);

        boolean ok = service.grantBadge(10L, 1);

        assertFalse(ok);
        verify(userBadgeMapper, never()).insert(any(UserBadge.class));
    }

    @Test
    void grantBadge_shouldReturnTrueWhenAlreadyOwned() {
        when(badgeMapper.selectOne(any())).thenReturn(Badge.builder().id(200L).build());
        when(userBadgeMapper.selectUserBadge(10L, 200L)).thenReturn(new UserBadge());

        boolean ok = service.grantBadge(10L, 2);

        assertTrue(ok);
        verify(userBadgeMapper, never()).insert(any(UserBadge.class));
    }

    @Test
    void grantBadge_shouldInsertWhenFirstGrant() {
        when(badgeMapper.selectOne(any())).thenReturn(Badge.builder().id(300L).build());
        when(userBadgeMapper.selectUserBadge(10L, 300L)).thenReturn(null);
        when(userBadgeMapper.insert(any(UserBadge.class))).thenReturn(1);

        boolean ok = service.grantBadge(10L, 3);

        assertTrue(ok);
        ArgumentCaptor<UserBadge> captor = ArgumentCaptor.forClass(UserBadge.class);
        verify(userBadgeMapper).insert(captor.capture());
        UserBadge inserted = captor.getValue();
        assertNotNull(inserted.getId());
        assertTrue(10L == inserted.getUserId());
        assertTrue(300L == inserted.getBadgeId());
        assertNotNull(inserted.getAcquireTime());
    }
}

