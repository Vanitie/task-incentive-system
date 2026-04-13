package com.whu.graduation.taskincentive.constant;

/**
 * 全局缓存与 MQ 相关常量
 */
public final class CacheKeys {

    private CacheKeys() {}

    // 消息去重前缀（每条消息独立 key）
    public static final String DEDUP_MSG_PREFIX = "mq:processed:"; // 使用后续拼接 messageId

    // Reward consumer 原先使用的 set（已不推荐）
    public static final String REWARD_DEDUP_SET = "mq:reward:processed";

    // Task config 缓存 key 前缀
    public static final String TASK_CONFIG_PREFIX = "taskConfig:";

    // 事件 -> taskId 集合前缀
    public static final String EVENT_TASKS_PREFIX = "event:";

    // 用户已接取任务集合前缀（只保存 taskId）
    public static final String USER_ACCEPTED_PREFIX = "user:accepted:";

    // DLQ / Error topic（可在配置中覆盖）
    public static final String DEFAULT_DLQ_TOPIC = "dlq-topic";

    // 风控决策落库主题
    public static final String RISK_DECISION_PERSIST_TOPIC = "risk-decision-persist-topic";

    // 用户行为日志落库主题（正式链路异步写）
    public static final String USER_ACTION_LOG_PERSIST_TOPIC = "user-action-log-persist-topic";

    // 其它常量
    public static final long DEFAULT_DEDUP_TTL_DAYS = 7L;
    public static final long DEFAULT_DEDUP_TTL_HOURS = 6L;
}
