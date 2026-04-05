package com.whu.graduation.taskincentive.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.whu.graduation.taskincentive.dao.entity.Badge;
import com.whu.graduation.taskincentive.dao.mapper.BadgeMapper;
import com.whu.graduation.taskincentive.testutil.ReflectionTestUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BadgeServiceImplTest {

    private BadgeMapper badgeMapper;
    private BadgeServiceImpl service;

    @BeforeEach
    void setUp() throws Exception {
        badgeMapper = mock(BadgeMapper.class);
        service = new BadgeServiceImpl();

        ReflectionTestUtils.setFieldRecursively(service, "baseMapper", badgeMapper);
    }

    @Test
    void save_shouldAssignIdAndInsert() {
        Badge badge = new Badge();
        when(badgeMapper.insert(any(Badge.class))).thenReturn(1);

        boolean ok = service.save(badge);

        assertTrue(ok);
        assertNotNull(badge.getId());
        verify(badgeMapper).insert(badge);
    }

    @Test
    void searchByName_shouldBuildLikeConditionWhenNamePresent() {
        Page<Badge> page = new Page<>(1, 5);
        Page<Badge> result = new Page<>(1, 5);
        result.setRecords(List.of(Badge.builder().name("alpha").build()));

        when(badgeMapper.selectPage(eq(page), any(QueryWrapper.class))).thenReturn(result);

        Page<Badge> out = service.searchByName("alpha", page);

        assertEquals(1, out.getRecords().size());
        ArgumentCaptor<QueryWrapper<Badge>> captor = ArgumentCaptor.forClass(QueryWrapper.class);
        verify(badgeMapper).selectPage(eq(page), captor.capture());
        String sqlSegment = String.valueOf(captor.getValue().getSqlSegment()).toLowerCase();
        assertTrue(sqlSegment.contains("like"));
        assertTrue(sqlSegment.contains("name"));
    }

    @Test
    void searchByName_shouldNotAddLikeWhenNameEmpty() {
        Page<Badge> page = new Page<>(1, 5);
        when(badgeMapper.selectPage(eq(page), any(QueryWrapper.class))).thenReturn(new Page<>(1, 5));

        service.searchByName("", page);

        ArgumentCaptor<QueryWrapper<Badge>> captor = ArgumentCaptor.forClass(QueryWrapper.class);
        verify(badgeMapper).selectPage(eq(page), captor.capture());
        String sqlSegment = String.valueOf(captor.getValue().getSqlSegment()).toLowerCase();
        assertTrue(!sqlSegment.contains("like"));
    }

    @Test
    void searchByName_shouldNotAddLikeWhenNameNull() {
        Page<Badge> page = new Page<>(1, 5);
        when(badgeMapper.selectPage(eq(page), any(QueryWrapper.class))).thenReturn(new Page<>(1, 5));

        service.searchByName(null, page);

        ArgumentCaptor<QueryWrapper<Badge>> captor = ArgumentCaptor.forClass(QueryWrapper.class);
        verify(badgeMapper).selectPage(eq(page), captor.capture());
        String sqlSegment = String.valueOf(captor.getValue().getSqlSegment()).toLowerCase();
        assertTrue(!sqlSegment.contains("like"));
    }
}

