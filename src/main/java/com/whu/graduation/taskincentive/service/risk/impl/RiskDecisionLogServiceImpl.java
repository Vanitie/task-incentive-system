package com.whu.graduation.taskincentive.service.risk.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.whu.graduation.taskincentive.dao.entity.RiskDecisionLog;
import com.whu.graduation.taskincentive.dao.mapper.RiskDecisionLogMapper;
import com.whu.graduation.taskincentive.service.risk.RiskDecisionLogService;
import jakarta.annotation.Resource;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Date;

/**
 * 风控决策日志服务实现
 */
@Service
@RequiredArgsConstructor
public class RiskDecisionLogServiceImpl implements RiskDecisionLogService {

    private final RiskDecisionLogMapper riskDecisionLogMapper;

    @Override
    public Page<RiskDecisionLog> page(Page<RiskDecisionLog> page, Long taskId, String decision, Date start, Date end) {
        QueryWrapper<RiskDecisionLog> wrapper = new QueryWrapper<>();
        if (taskId != null) wrapper.eq("task_id", taskId);
        if (decision != null && !decision.isEmpty()) wrapper.eq("decision", decision);
        if (start != null) wrapper.ge("created_at", start);
        if (end != null) wrapper.lt("created_at", end);
        wrapper.orderByDesc("created_at");
        return riskDecisionLogMapper.selectPage(page, wrapper);
    }
}
