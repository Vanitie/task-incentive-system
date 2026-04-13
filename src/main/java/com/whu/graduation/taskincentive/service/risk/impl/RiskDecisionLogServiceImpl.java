package com.whu.graduation.taskincentive.service.risk.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.whu.graduation.taskincentive.dao.entity.RiskDecisionLog;
import com.whu.graduation.taskincentive.dao.entity.TaskConfig;
import com.whu.graduation.taskincentive.dao.entity.User;
import com.whu.graduation.taskincentive.dao.mapper.RiskDecisionLogMapper;
import com.whu.graduation.taskincentive.dao.mapper.TaskConfigMapper;
import com.whu.graduation.taskincentive.dao.mapper.UserMapper;
import com.whu.graduation.taskincentive.service.risk.RiskDecisionLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 风控决策日志服务实现
 */
@Service
@RequiredArgsConstructor
public class RiskDecisionLogServiceImpl implements RiskDecisionLogService {

    private final RiskDecisionLogMapper riskDecisionLogMapper;
    private final UserMapper userMapper;
    private final TaskConfigMapper taskConfigMapper;

    @Override
    public Page<RiskDecisionLog> page(Page<RiskDecisionLog> page,
                                      Long taskId,
                                      Long userId,
                                      String userName,
                                      String taskName,
                                      String decision,
                                      Date start,
                                      Date end) {
        QueryWrapper<RiskDecisionLog> wrapper = new QueryWrapper<>();
        if (taskId != null) wrapper.eq("task_id", taskId);
        if (userId != null) wrapper.eq("user_id", userId);
        if (decision != null && !decision.isEmpty()) wrapper.eq("decision", decision);
        if (start != null) wrapper.ge("created_at", start);
        if (end != null) wrapper.lt("created_at", end);

        if (userName != null && !userName.trim().isEmpty()) {
            List<Long> userIds = userMapper.selectList(
                            Wrappers.lambdaQuery(User.class)
                                    .select(User::getId)
                                    .like(User::getUsername, userName.trim()))
                    .stream()
                    .map(User::getId)
                    .collect(Collectors.toList());
            if (userIds.isEmpty()) {
                return emptyPage(page);
            }
            wrapper.in("user_id", userIds);
        }

        if (taskName != null && !taskName.trim().isEmpty()) {
            List<Long> taskIds = taskConfigMapper.selectList(
                            Wrappers.lambdaQuery(TaskConfig.class)
                                    .select(TaskConfig::getId)
                                    .like(TaskConfig::getTaskName, taskName.trim()))
                    .stream()
                    .map(TaskConfig::getId)
                    .collect(Collectors.toList());
            if (taskIds.isEmpty()) {
                return emptyPage(page);
            }
            wrapper.in("task_id", taskIds);
        }

        wrapper.orderByDesc("created_at");
        Page<RiskDecisionLog> result = riskDecisionLogMapper.selectPage(page, wrapper);

        List<RiskDecisionLog> records = result.getRecords();
        if (records == null || records.isEmpty()) {
            return result;
        }

        Set<Long> userIds = records.stream()
                .map(RiskDecisionLog::getUserId)
                .filter(id -> id != null && id > 0)
                .collect(Collectors.toSet());
        Set<Long> taskIds = records.stream()
                .map(RiskDecisionLog::getTaskId)
                .filter(id -> id != null && id > 0)
                .collect(Collectors.toSet());

        Map<Long, String> userNameMap = userIds.isEmpty()
                ? Collections.emptyMap()
                : userMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, User::getUsername, (a, b) -> a));

        Map<Long, String> taskNameMap = taskIds.isEmpty()
                ? Collections.emptyMap()
                : taskConfigMapper.selectBatchIds(taskIds).stream()
                .collect(Collectors.toMap(TaskConfig::getId, TaskConfig::getTaskName, (a, b) -> a));

        for (RiskDecisionLog record : records) {
            record.setUserName(userNameMap.getOrDefault(record.getUserId(), "-"));
            record.setTaskName(taskNameMap.getOrDefault(record.getTaskId(), "-"));
        }

        return result;
    }

    private Page<RiskDecisionLog> emptyPage(Page<RiskDecisionLog> page) {
        Page<RiskDecisionLog> empty = new Page<>(page.getCurrent(), page.getSize());
        empty.setTotal(0L);
        empty.setRecords(Collections.emptyList());
        return empty;
    }
}
