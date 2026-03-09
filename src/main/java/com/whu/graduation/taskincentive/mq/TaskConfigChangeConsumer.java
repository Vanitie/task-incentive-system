package com.whu.graduation.taskincentive.mq;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.whu.graduation.taskincentive.config.InstanceInfo;
import com.whu.graduation.taskincentive.service.TaskConfigService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.listener.AcknowledgingMessageListener;
import org.springframework.kafka.listener.ConcurrentMessageListenerContainer;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 程序化创建 Kafka consumer，使每个应用实例使用唯一 groupId，确保每台实例都消费到消息（每实例都能收到全部消息）
 */
@Slf4j
@Component
public class TaskConfigChangeConsumer {

    @Autowired
    private TaskConfigService taskConfigService;

    @Autowired
    private InstanceInfo instanceInfo;

    @Autowired
    private ConcurrentKafkaListenerContainerFactory<String, String> factory;

    // 从配置读取 topic，默认与 canal.properties 中一致
    @Value("${canal.mq.topic:task-config-change}")
    private String topic;

    private ConcurrentMessageListenerContainer<String, String> container;

    @PostConstruct
    public void init() {
        String groupId = "task-config-change-group-" + instanceInfo.getInstanceId();

        // 使用 factory 创建 container（传入 topic 名称）
        container = factory.createContainer(topic);
        container.getContainerProperties().setGroupId(groupId);

        // 使用 MANUAL_IMMEDIATE 确保可以在处理成功后立刻提交 offset
        container.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);

        // 注册消息监听器：支持手动 ack，并处理多行数据与不同字段名（taskId / task_id / id）
        container.setupMessageListener(new AcknowledgingMessageListener<String, String>() {
            @Override
            public void onMessage(ConsumerRecord<String, String> record, Acknowledgment acknowledgment) {
                String value = record.value();
                // 将复杂逻辑提取到独立方法，便于单元测试
                handleMessage(value, acknowledgment);
            }
        });

        container.setAutoStartup(true);
        container.setConcurrency(1);
        container.start();

        log.info("TaskConfigChangeConsumer started with groupId={} topic={}", groupId, topic);
    }

    // 提取处理方法，包内可见，便于不启动 Spring 的单元测试直接调用
    void handleMessage(String value, Acknowledgment acknowledgment) {
        String groupId = "task-config-change-group-" + (instanceInfo == null ? "-unknown" : instanceInfo.getInstanceId());
        try {
            // log record metadata to help debugging/traceability
            // 这里不再有 ConsumerRecord 的元数据，只有 value

            JSONObject json = JSON.parseObject(value);

            // 最早判断：只处理 task_config 表的变更，其他表直接丢弃并 ack，避免不必要的解析开销
            String table = json.getString("table");
            if (table == null || !"task_config".equalsIgnoreCase(table)) {
                log.debug("skip unrelated table change, table={}, topic={}, payload={}", table, topic, value);
                if (Objects.nonNull(acknowledgment)) {
                    try {
                        acknowledgment.acknowledge();
                    } catch (Exception ackEx) {
                        log.warn("error while acknowledging skipped message", ackEx);
                    }
                }
                return;
            }

            log.info("received task-config-change event, instanceGroup={}, topic={}, payload={}", groupId, topic, value);

            String type = json.getString("type"); // INSERT/UPDATE/DELETE

            // collect ids from data or old depending on type
            List<Long> ids = new ArrayList<>();
            JSONArray dataArr = json.getJSONArray("data");
            JSONArray oldArr = json.getJSONArray("old");

            if ("DELETE".equalsIgnoreCase(type) && oldArr != null && !oldArr.isEmpty()) {
                for (int i = 0; i < oldArr.size(); i++) {
                    JSONObject row = oldArr.getJSONObject(i);
                    Long id = extractTaskId(row);
                    if (id != null) ids.add(id);
                }
            } else if (dataArr != null && !dataArr.isEmpty()) {
                for (int i = 0; i < dataArr.size(); i++) {
                    JSONObject row = dataArr.getJSONObject(i);
                    Long id = extractTaskId(row);
                    if (id != null) ids.add(id);
                }
            }

            if (ids.isEmpty()) {
                log.info("no taskId found in message, skipping specific refresh (table={})", table);
            } else {
                // 仅处理 task_config 表（已在上方预判过）
                for (Long taskId : ids) {
                    try {
                        taskConfigService.invalidateTaskConfig(taskId);
                        taskConfigService.refreshTaskConfig(taskId);
                    } catch (Exception e) {
                        // 单条失败不影响其它条目，记录并继续
                        log.error("failed to refresh task config for id={} ", taskId, e);
                    }
                }
            }

            // 处理成功后手动提交 offset
            if (Objects.nonNull(acknowledgment)) {
                acknowledgment.acknowledge();
            }
        } catch (Exception e) {
            // 解析/处理失败：记录完整信息并提交 offset，避免单条消息阻塞整个分区。
            // 如果你需要重试或 DLQ，可在这里实现重试策略或将消息发往错误 topic
            log.error("failed to handle task-config-change message, will ack and skip to avoid blocking. payload={}", value, e);
            try {
                if (Objects.nonNull(acknowledgment)) {
                    acknowledgment.acknowledge();
                }
            } catch (Exception ackEx) {
                log.error("error while acknowledging failed message", ackEx);
            }
        }
    }

    @PreDestroy
    public void shutdown() {
        if (container != null) {
            try {
                container.stop();
            } catch (Exception e) {
                log.warn("error while stopping TaskConfigChangeConsumer container", e);
            }
        }
    }

    private Long extractTaskId(JSONObject row) {
        if (row == null) return null;
        // 常见的主键字段名：taskId, task_id, id
        if (row.containsKey("taskId")) {
            Object v = row.get("taskId");
            Long parsed = parseLongFlexible(v);
            if (parsed != null) return parsed;
        }
        if (row.containsKey("task_id")) {
            Object v = row.get("task_id");
            Long parsed = parseLongFlexible(v);
            if (parsed != null) return parsed;
        }
        if (row.containsKey("id")) {
            Object v = row.get("id");
            Long parsed = parseLongFlexible(v);
            if (parsed != null) return parsed;
        }
        // 如果没有明确主键，可以尝试查找第一个数值字段（不推荐，但作为兜底）
        for (String key : row.keySet()) {
            Object v = row.get(key);
            Long parsed = parseLongFlexible(v);
            if (parsed != null) return parsed;
        }
        return null;
    }

    private Long parseLongFlexible(Object v) {
        if (v == null) return null;
        if (v instanceof Number) {
            return ((Number) v).longValue();
        }
        String s = v.toString();
        if (s.isEmpty()) return null;
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            try {
                BigDecimal bd = new BigDecimal(s);
                return bd.longValue();
            } catch (Exception ex) {
                return null;
            }
        }
    }
}
