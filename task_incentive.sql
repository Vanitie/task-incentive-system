/*
 Navicat Premium Data Transfer

 Source Server         : localhost_3306
 Source Server Type    : MySQL
 Source Server Version : 80045 (8.0.45)
 Source Host           : localhost:3306
 Source Schema         : task_incentive

 Target Server Type    : MySQL
 Target Server Version : 80045 (8.0.45)
 File Encoding         : 65001

 Date: 12/04/2026 18:31:29
*/

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- Table structure for badge
-- ----------------------------
DROP TABLE IF EXISTS `badge`;
CREATE TABLE `badge`  (
  `id` bigint NOT NULL COMMENT '徽章ID，雪花ID',
  `name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '徽章名称',
  `code` int NOT NULL COMMENT '徽章标识，与奖励值对应',
  `image_url` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '徽章图片URL',
  `description` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '徽章描述',
  `create_time` datetime NOT NULL COMMENT '创建时间',
  `update_time` datetime NOT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `idx_code`(`code` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '徽章表，存储徽章静态信息' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for reward_freeze_record
-- ----------------------------
DROP TABLE IF EXISTS `reward_freeze_record`;
CREATE TABLE `reward_freeze_record`  (
  `id` bigint NOT NULL COMMENT '冻结记录ID',
  `reward_id` bigint NULL DEFAULT NULL COMMENT '奖励ID',
  `user_id` bigint NULL DEFAULT NULL COMMENT '用户ID',
  `task_id` bigint NULL DEFAULT NULL COMMENT '任务ID',
  `freeze_reason` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '冻结原因',
  `status` tinyint NOT NULL COMMENT '状态：0冻结/1解冻',
  `unfreeze_at` datetime NULL DEFAULT NULL COMMENT '解冻时间',
  `created_at` datetime NOT NULL COMMENT '创建时间',
  `updated_at` datetime NOT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '奖励冻结记录' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for risk_blacklist
-- ----------------------------
DROP TABLE IF EXISTS `risk_blacklist`;
CREATE TABLE `risk_blacklist`  (
  `id` bigint NOT NULL COMMENT '黑名单ID',
  `target_type` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '目标类型：USER/DEVICE/IP',
  `target_value` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '目标值',
  `source` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '来源',
  `expire_at` datetime NULL DEFAULT NULL COMMENT '过期时间',
  `status` tinyint NOT NULL COMMENT '状态：0禁用/1启用',
  `created_at` datetime NOT NULL COMMENT '创建时间',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '黑名单' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for risk_decision_log
-- ----------------------------
DROP TABLE IF EXISTS `risk_decision_log`;
CREATE TABLE `risk_decision_log`  (
  `id` bigint NOT NULL COMMENT '日志ID',
  `request_id` varchar(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '请求ID',
  `event_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '事件ID',
  `user_id` bigint NULL DEFAULT NULL COMMENT '用户ID',
  `task_id` bigint NULL DEFAULT NULL COMMENT '任务ID',
  `decision` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '决策结果',
  `reason_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '原因码',
  `hit_rules` json NULL COMMENT '命中规则',
  `risk_score` int NULL DEFAULT 0 COMMENT '风险分',
  `latency_ms` bigint NULL DEFAULT 0 COMMENT '耗时ms',
  `created_at` datetime NOT NULL COMMENT '创建时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_request_id`(`request_id` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '风控决策日志' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for risk_quota
-- ----------------------------
DROP TABLE IF EXISTS `risk_quota`;
CREATE TABLE `risk_quota`  (
  `id` bigint NOT NULL COMMENT '配额ID',
  `quota_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '配额名称',
  `scope_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '作用域类型',
  `scope_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '作用域ID',
  `period_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '周期类型',
  `limit_value` int NOT NULL COMMENT '限额值',
  `used_value` int NOT NULL DEFAULT 0 COMMENT '已使用',
  `reset_at` datetime NULL DEFAULT NULL COMMENT '重置时间',
  `created_at` datetime NOT NULL COMMENT '创建时间',
  `resource_type` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'ALL' COMMENT '资源类型（POINT/BADGE/PHYSICAL/ALL 等)',
  `resource_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'ALL' COMMENT '资源ID（具体奖品/徽章ID；无区分时用 ALL）',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_quota_name`(`quota_name` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '风控配额' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for risk_rule
-- ----------------------------
DROP TABLE IF EXISTS `risk_rule`;
CREATE TABLE `risk_rule`  (
  `id` bigint NOT NULL COMMENT '规则ID',
  `name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '规则名称',
  `type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '规则类型',
  `priority` int NOT NULL COMMENT '优先级',
  `status` tinyint NOT NULL COMMENT '状态：0草稿/1启用/2停用',
  `condition_expr` varchar(1024) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '条件表达式',
  `action` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '动作',
  `action_params` varchar(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '动作参数',
  `start_time` datetime NULL DEFAULT NULL COMMENT '生效开始时间',
  `end_time` datetime NULL DEFAULT NULL COMMENT '生效结束时间',
  `version` int NOT NULL COMMENT '版本号',
  `created_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '创建人',
  `updated_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '更新人',
  `created_at` datetime NOT NULL COMMENT '创建时间',
  `updated_at` datetime NOT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '风控规则' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for risk_whitelist
-- ----------------------------
DROP TABLE IF EXISTS `risk_whitelist`;
CREATE TABLE `risk_whitelist`  (
  `id` bigint NOT NULL COMMENT '白名单ID',
  `target_type` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '目标类型：USER/DEVICE/IP',
  `target_value` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '目标值',
  `source` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '来源',
  `expire_at` datetime NULL DEFAULT NULL COMMENT '过期时间',
  `status` tinyint NOT NULL COMMENT '状态：0禁用/1启用',
  `created_at` datetime NOT NULL COMMENT '创建时间',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '白名单' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for task_config
-- ----------------------------
DROP TABLE IF EXISTS `task_config`;
CREATE TABLE `task_config`  (
  `id` bigint NOT NULL COMMENT '任务ID',
  `task_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '任务名称',
  `task_type` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '任务类型：行为 TASK_TYPE_BEHAVIOR / 阶梯 TASK_TYPE_STAIR / 限量 TASK_TYPE_LIMITED',
  `stock_type` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'STOCK_TYPE_UNLIMITED' COMMENT '库存类型：无限 STOCK_TYPE_UNLIMITED / 限量 STOCK_TYPE_LIMITED',
  `trigger_event` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '触发事件类型，例如 USER_LEARN / USER_SIGN',
  `rule_config` json NULL COMMENT '策略配置，例如目标值、连续天数、阶梯规则等',
  `reward_type` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '奖励类型：积分 REWARD_POINT / 徽章 REWARD_BADGE / 实物 REWARD_PHYSICAL',
  `reward_value` int NOT NULL COMMENT '奖励数值或数量',
  `total_stock` int NULL DEFAULT NULL COMMENT '限量任务总库存，仅限量任务使用',
  `status` tinyint NOT NULL COMMENT '任务状态：停用 0 / 启用 1',
  `start_time` datetime NOT NULL COMMENT '任务开始时间',
  `end_time` datetime NOT NULL COMMENT '任务结束时间',
  `create_time` datetime NOT NULL COMMENT '创建时间',
  `update_time` datetime NOT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_status`(`status` ASC) USING BTREE,
  INDEX `idx_task_type`(`task_type` ASC) USING BTREE,
  INDEX `idx_trigger_event`(`trigger_event` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '任务模板表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for task_config_history
-- ----------------------------
DROP TABLE IF EXISTS `task_config_history`;
CREATE TABLE `task_config_history`  (
  `id` bigint NOT NULL COMMENT '历史记录ID',
  `task_id` bigint NOT NULL COMMENT '任务ID',
  `version_no` int NOT NULL COMMENT '版本号，从1递增',
  `task_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '任务名称',
  `task_type` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '任务类型',
  `stock_type` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '库存类型',
  `trigger_event` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '触发事件类型',
  `rule_config` json NULL COMMENT '任务规则配置快照',
  `reward_type` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '奖励类型',
  `reward_value` int NOT NULL COMMENT '奖励值',
  `total_stock` int NULL DEFAULT NULL COMMENT '总库存',
  `status` tinyint NOT NULL COMMENT '任务状态',
  `start_time` datetime NOT NULL COMMENT '任务开始时间',
  `end_time` datetime NOT NULL COMMENT '任务结束时间',
  `source_update_time` datetime NULL DEFAULT NULL COMMENT 'task_config.update_time快照',
  `change_type` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '变更类型：CREATE/UPDATE',
  `changed_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT 'system' COMMENT '变更人',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '历史记录写入时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_task_version`(`task_id` ASC, `version_no` ASC) USING BTREE,
  INDEX `idx_task_history_ctime`(`task_id` ASC, `create_time` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '任务配置历史版本表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for task_stock
-- ----------------------------
DROP TABLE IF EXISTS `task_stock`;
CREATE TABLE `task_stock`  (
  `task_id` bigint NOT NULL COMMENT '任务ID',
  `stage_index` int NOT NULL DEFAULT 1 COMMENT '阶梯任务的阶段序号，普通任务为1',
  `available_stock` int NOT NULL COMMENT '剩余库存数量',
  `version` int NOT NULL COMMENT '乐观锁版本号，用于高并发扣减',
  `create_time` datetime NOT NULL COMMENT '创建时间',
  `update_time` datetime NOT NULL COMMENT '更新时间',
  PRIMARY KEY (`task_id`, `stage_index`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '任务库存表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for user
-- ----------------------------
DROP TABLE IF EXISTS `user`;
CREATE TABLE `user`  (
  `id` bigint NOT NULL COMMENT '用户ID',
  `username` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '用户名',
  `PASSWORD` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '密码（BCrypt 加密保存）',
  `roles` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT 'ROLE_USER' COMMENT '角色，逗号分隔（例如：ROLE_USER,ROLE_ADMIN）',
  `point_balance` int NOT NULL DEFAULT 0 COMMENT '当前积分余额',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '用户创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '用户更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `idx_username`(`username` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '用户表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for user_action_log
-- ----------------------------
DROP TABLE IF EXISTS `user_action_log`;
CREATE TABLE `user_action_log`  (
  `id` bigint NOT NULL COMMENT '行为日志ID',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `action_type` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '行为类型：用户学习 USER_LEARN / 用户签到 USER_SIGN / 其他 OTHER',
  `action_value` int NOT NULL COMMENT '行为数值，例如分钟数/次数',
  `create_time` datetime NOT NULL COMMENT '行为发生时间',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_user_time`(`user_id` ASC, `create_time` ASC) USING BTREE,
  INDEX `idx_action_type`(`action_type` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '用户行为日志表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for user_badge
-- ----------------------------
DROP TABLE IF EXISTS `user_badge`;
CREATE TABLE `user_badge`  (
  `id` bigint NOT NULL COMMENT 'ID，雪花ID',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `badge_id` bigint NOT NULL COMMENT '徽章ID',
  `acquire_time` datetime NOT NULL COMMENT '获得时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `idx_user_badge`(`user_id` ASC, `badge_id` ASC) USING BTREE,
  INDEX `idx_badge_id`(`badge_id` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '用户徽章关联表，记录用户获得的徽章' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for user_reward_record
-- ----------------------------
DROP TABLE IF EXISTS `user_reward_record`;
CREATE TABLE `user_reward_record`  (
  `id` bigint NOT NULL COMMENT '奖励记录ID',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `task_id` bigint NOT NULL COMMENT '任务ID',
  `reward_type` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '奖励类型：积分 REWARD_POINT / 徽章 REWARD_BADGE / 实物 REWARD_PHYSICAL',
  `status` tinyint NULL DEFAULT 1 COMMENT '奖励状态（仅实物奖励有效）：未领取 0 / 已领取 1',
  `message_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT 'MQ消息唯一标识（用于幂等）',
  `reward_id` bigint NULL DEFAULT NULL COMMENT '奖励业务ID',
  `grant_status` tinyint NULL DEFAULT 2 COMMENT '发放状态：0-初始化，1-处理中，2-成功，3-失败',
  `error_msg` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '发放失败原因',
  `reward_value` int NOT NULL COMMENT '奖励数值或数量',
  `create_time` datetime NOT NULL COMMENT '发放时间',
  `update_time` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_user_reward_record_message_id`(`message_id` ASC) USING BTREE,
  INDEX `idx_user_id`(`user_id` ASC) USING BTREE,
  INDEX `idx_task_id`(`task_id` ASC) USING BTREE,
  INDEX `idx_user_reward_record_grant_status`(`grant_status` ASC) USING BTREE,
  INDEX `idx_user_reward_record_reward_id`(`reward_id` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '用户奖励记录表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for user_task_instance
-- ----------------------------
DROP TABLE IF EXISTS `user_task_instance`;
CREATE TABLE `user_task_instance`  (
  `id` bigint NOT NULL COMMENT '任务实例ID',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `user_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '用户名',
  `task_id` bigint NOT NULL COMMENT '任务ID',
  `task_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '任务名',
  `progress` int NOT NULL DEFAULT 0 COMMENT '当前完成进度',
  `status` tinyint NOT NULL DEFAULT 0 COMMENT '0未完成/1完成',
  `version` int NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  `extra_data` json NULL COMMENT '额外数据，例如连续签到天数',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `idx_user_task`(`user_id` ASC, `task_id` ASC) USING BTREE,
  INDEX `idx_status`(`status` ASC) USING BTREE,
  INDEX `idx_task_id`(`task_id` ASC) USING BTREE,
  INDEX `idx_user_task_name`(`user_name` ASC, `task_name` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '用户任务实例表' ROW_FORMAT = DYNAMIC;

SET FOREIGN_KEY_CHECKS = 1;
