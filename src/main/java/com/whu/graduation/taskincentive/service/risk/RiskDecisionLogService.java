package com.whu.graduation.taskincentive.service.risk;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.whu.graduation.taskincentive.dao.entity.RiskDecisionLog;

import java.util.Date;

/**
 * 风控决策日志服务
 */
public interface RiskDecisionLogService {
    Page<RiskDecisionLog> page(Page<RiskDecisionLog> page,
                               Long taskId,
                               Long userId,
                               String userName,
                               String taskName,
                               String decision,
                               Date start,
                               Date end);
}
