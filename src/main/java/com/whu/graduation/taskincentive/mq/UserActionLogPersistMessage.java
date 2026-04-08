package com.whu.graduation.taskincentive.mq;

import com.whu.graduation.taskincentive.dao.entity.UserActionLog;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 用户行为日志落库消息
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserActionLogPersistMessage {
    private UserActionLog actionLog;
}

