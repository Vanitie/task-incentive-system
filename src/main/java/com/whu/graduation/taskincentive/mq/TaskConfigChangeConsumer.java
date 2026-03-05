package com.whu.graduation.taskincentive.mq;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.whu.graduation.taskincentive.config.InstanceInfo;
import com.whu.graduation.taskincentive.service.TaskConfigService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.listener.ConcurrentMessageListenerContainer;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.MessageListener;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

/**
 * 程序化创建 Kafka consumer，使每个应用实例使用唯一 groupId，确保每台实例都消费到消息
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

    @PostConstruct
    public void init() {
        String groupId = "task-config-change-group-" + instanceInfo.getInstanceId();

        // 使用 factory 创建 container（传入 topic 名称）
        ConcurrentMessageListenerContainer<String, String> container = factory.createContainer("task-config-change");
        container.getContainerProperties().setGroupId(groupId);

        // 注册消息监听器
        container.setupMessageListener((MessageListener<String, String>) record -> {
            String value = record.value();
            try {
                JSONObject json = JSON.parseObject(value);
                log.info("received task-config-change event, instanceGroup={}, payload={}", groupId, value);
                String table = json.getString("table");
                if ("task_config".equals(table)) {
                    Long taskId = json.getLong("taskId");
                    if (taskId != null) {
                        taskConfigService.invalidateTaskConfig(taskId);
                        taskConfigService.refreshTaskConfig(taskId);
                    } else {
                        log.info("task-config-change without taskId received, skipping specific refresh");
                    }
                }
            } catch (Exception e) {
                log.warn("failed to handle task-config-change message", e);
            }
        });

        container.setAutoStartup(true);
        container.setConcurrency(1);
        container.start();

        log.info("TaskConfigChangeConsumer started with groupId={}", groupId);
    }
}
