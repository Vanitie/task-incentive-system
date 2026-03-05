package com.whu.graduation.taskincentive.binlog;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 占位说明：Binlog 推送职责已交给 Canal外部 CDC 工具。
 *
 * 部署 Canal Server + canal-adapter（或 Debezium）来监听 MySQL 的 `task_config` 表变更，
 * 并将变更事件发布到 Kafka topic `task-config-change`（消息中包含 taskId、op、updateTime 等）。
 *
 * 此组件作为占位，不在应用中实现轮询或直接读取 binlog，以避免在应用内耦合 binlog 客户端或额外的依赖。
 */
@Slf4j
@Component
public class BinlogToKafkaProducer {

    // 该类保留为占位，实际使用Canal来推送变更到 Kafka。
    public BinlogToKafkaProducer() {
        log.info("BinlogToKafkaProducer placeholder active: use Canal/Debezium to publish task_config changes to Kafka topic 'task-config-change'.");
    }
}
