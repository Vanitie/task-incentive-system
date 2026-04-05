package com.whu.graduation.taskincentive.service.risk;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.whu.graduation.taskincentive.dao.entity.RiskDecisionLog;
import com.whu.graduation.taskincentive.dao.mapper.RiskDecisionLogMapper;
import com.whu.graduation.taskincentive.service.risk.impl.RiskDecisionLogServiceImpl;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RiskDecisionLogServiceImplTest {

    @Test
    void page_shouldApplyAllFiltersWhenProvided() {
        RiskDecisionLogMapper mapper = mock(RiskDecisionLogMapper.class);
        RiskDecisionLogServiceImpl service = new RiskDecisionLogServiceImpl(mapper);

        Page<RiskDecisionLog> expected = new Page<>(1, 20);
        when(mapper.selectPage(any(), any())).thenReturn(expected);

        Date start = new Date(System.currentTimeMillis() - 1000);
        Date end = new Date();
        Page<RiskDecisionLog> result = service.page(new Page<>(1, 20), 10L, "REJECT", start, end);

        assertEquals(expected, result);

        ArgumentCaptor<QueryWrapper<RiskDecisionLog>> wrapperCaptor = ArgumentCaptor.forClass(QueryWrapper.class);
        verify(mapper).selectPage(any(), wrapperCaptor.capture());
        QueryWrapper<RiskDecisionLog> wrapper = wrapperCaptor.getValue();
        String sqlSegment = wrapper.getSqlSegment();
        assertTrue(sqlSegment.contains("task_id"));
        assertTrue(sqlSegment.contains("decision"));
        assertTrue(sqlSegment.contains("created_at"));
    }

    @Test
    void page_shouldSkipDecisionFilterWhenEmpty() {
        RiskDecisionLogMapper mapper = mock(RiskDecisionLogMapper.class);
        RiskDecisionLogServiceImpl service = new RiskDecisionLogServiceImpl(mapper);

        Page<RiskDecisionLog> expected = new Page<>(1, 20);
        when(mapper.selectPage(any(), any())).thenReturn(expected);

        service.page(new Page<>(1, 20), null, "", null, null);

        ArgumentCaptor<QueryWrapper<RiskDecisionLog>> wrapperCaptor = ArgumentCaptor.forClass(QueryWrapper.class);
        verify(mapper).selectPage(any(), wrapperCaptor.capture());
        QueryWrapper<RiskDecisionLog> wrapper = wrapperCaptor.getValue();
        String sqlSegment = wrapper.getSqlSegment();
        assertTrue(sqlSegment.contains("created_at"));
    }
}
