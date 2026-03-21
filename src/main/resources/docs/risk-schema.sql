-- 风控最小可用表结构

CREATE TABLE risk_rule (
  id BIGINT PRIMARY KEY COMMENT '规则ID',
  name VARCHAR(128) NOT NULL COMMENT '规则名称',
  type VARCHAR(32) NOT NULL COMMENT '规则类型',
  priority INT NOT NULL COMMENT '优先级',
  status TINYINT NOT NULL COMMENT '状态：0草稿/1启用/2停用',
  condition_expr VARCHAR(1024) NOT NULL COMMENT '条件表达式',
  action VARCHAR(32) NOT NULL COMMENT '动作',
  action_params VARCHAR(256) DEFAULT NULL COMMENT '动作参数',
  start_time DATETIME DEFAULT NULL COMMENT '生效开始时间',
  end_time DATETIME DEFAULT NULL COMMENT '生效结束时间',
  version INT NOT NULL COMMENT '版本号',
  created_by VARCHAR(64) DEFAULT NULL COMMENT '创建人',
  updated_by VARCHAR(64) DEFAULT NULL COMMENT '更新人',
  created_at DATETIME NOT NULL COMMENT '创建时间',
  updated_at DATETIME NOT NULL COMMENT '更新时间'
) COMMENT='风控规则';

CREATE TABLE risk_decision_log (
  id BIGINT PRIMARY KEY COMMENT '日志ID',
  request_id VARCHAR(64) NOT NULL COMMENT '请求ID',
  event_id VARCHAR(64) DEFAULT NULL COMMENT '事件ID',
  user_id BIGINT DEFAULT NULL COMMENT '用户ID',
  task_id BIGINT DEFAULT NULL COMMENT '任务ID',
  decision VARCHAR(32) NOT NULL COMMENT '决策结果',
  reason_code VARCHAR(64) DEFAULT NULL COMMENT '原因码',
  hit_rules JSON DEFAULT NULL COMMENT '命中规则',
  risk_score INT DEFAULT 0 COMMENT '风险分',
  latency_ms BIGINT DEFAULT 0 COMMENT '耗时ms',
  created_at DATETIME NOT NULL COMMENT '创建时间',
  UNIQUE KEY uk_request_id (request_id)
) COMMENT='风控决策日志';

CREATE TABLE risk_quota (
  id BIGINT PRIMARY KEY COMMENT '配额ID',
  scope_type VARCHAR(32) NOT NULL COMMENT '作用域类型',
  scope_id VARCHAR(64) NOT NULL COMMENT '作用域ID',
  period_type VARCHAR(32) NOT NULL COMMENT '周期类型',
  limit_value INT NOT NULL COMMENT '限额值',
  used_value INT NOT NULL DEFAULT 0 COMMENT '已使用',
  reset_at DATETIME DEFAULT NULL COMMENT '重置时间',
  created_at DATETIME NOT NULL COMMENT '创建时间',
  UNIQUE KEY uk_scope_period (scope_type, scope_id, period_type)
) COMMENT='风控配额';

CREATE TABLE risk_blacklist (
  id BIGINT PRIMARY KEY COMMENT '黑名单ID',
  target_type VARCHAR(16) NOT NULL COMMENT '目标类型：USER/DEVICE/IP',
  target_value VARCHAR(64) NOT NULL COMMENT '目标值',
  source VARCHAR(64) DEFAULT NULL COMMENT '来源',
  expire_at DATETIME DEFAULT NULL COMMENT '过期时间',
  status TINYINT NOT NULL COMMENT '状态：0禁用/1启用',
  created_at DATETIME NOT NULL COMMENT '创建时间'
) COMMENT='黑名单';

CREATE TABLE risk_whitelist (
  id BIGINT PRIMARY KEY COMMENT '白名单ID',
  target_type VARCHAR(16) NOT NULL COMMENT '目标类型：USER/DEVICE/IP',
  target_value VARCHAR(64) NOT NULL COMMENT '目标值',
  source VARCHAR(64) DEFAULT NULL COMMENT '来源',
  expire_at DATETIME DEFAULT NULL COMMENT '过期时间',
  status TINYINT NOT NULL COMMENT '状态：0禁用/1启用',
  created_at DATETIME NOT NULL COMMENT '创建时间'
) COMMENT='白名单';

CREATE TABLE reward_freeze_record (
  id BIGINT PRIMARY KEY COMMENT '冻结记录ID',
  reward_id BIGINT DEFAULT NULL COMMENT '奖励ID',
  user_id BIGINT DEFAULT NULL COMMENT '用户ID',
  task_id BIGINT DEFAULT NULL COMMENT '任务ID',
  freeze_reason VARCHAR(128) DEFAULT NULL COMMENT '冻结原因',
  status TINYINT NOT NULL COMMENT '状态：0冻结/1解冻',
  unfreeze_at DATETIME DEFAULT NULL COMMENT '解冻时间',
  created_at DATETIME NOT NULL COMMENT '创建时间',
  updated_at DATETIME NOT NULL COMMENT '更新时间'
) COMMENT='奖励冻结记录';
