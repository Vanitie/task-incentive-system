package com.whu.graduation.taskincentive.config;

import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.UUID;

/**
 * 应用实例信息：用于生成每台机器唯一ID，作为 Kafka consumer group 后缀
 */
@Component
public class InstanceInfo {

    private String instanceId;

    @PostConstruct
    public void init() {
        this.instanceId = UUID.randomUUID().toString();
    }

    public String getInstanceId() {
        return instanceId;
    }
}
