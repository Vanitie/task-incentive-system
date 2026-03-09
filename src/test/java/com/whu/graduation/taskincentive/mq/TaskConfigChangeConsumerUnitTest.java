package com.whu.graduation.taskincentive.mq;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.whu.graduation.taskincentive.config.InstanceInfo;
import com.whu.graduation.taskincentive.service.TaskConfigService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.support.Acknowledgment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.*;

/**
 * 单元测试 TaskConfigChangeConsumer 的核心处理逻辑（不依赖 Kafka/Redis/Docker）。
 */
public class TaskConfigChangeConsumerUnitTest {

    private TaskConfigChangeConsumer consumer;
    private TaskConfigService taskConfigService;
    private InstanceInfo instanceInfo;
    private Acknowledgment ack;

    @BeforeEach
    public void setup() {
        taskConfigService = mock(TaskConfigService.class);
        instanceInfo = mock(InstanceInfo.class);
        when(instanceInfo.getInstanceId()).thenReturn("unit-test-instance");
        ack = mock(Acknowledgment.class);

        consumer = new TaskConfigChangeConsumer();
        // 通过反射注入字段（consumer 字段是包私有且用 @Autowired，直接设置）
        try {
            java.lang.reflect.Field f1 = TaskConfigChangeConsumer.class.getDeclaredField("taskConfigService");
            f1.setAccessible(true);
            f1.set(consumer, taskConfigService);

            java.lang.reflect.Field f2 = TaskConfigChangeConsumer.class.getDeclaredField("instanceInfo");
            f2.setAccessible(true);
            f2.set(consumer, instanceInfo);

            java.lang.reflect.Field f3 = TaskConfigChangeConsumer.class.getDeclaredField("topic");
            f3.setAccessible(true);
            f3.set(consumer, "task-config-change");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void handleMessage_shouldCallService_forInsert() {
        JSONObject msg = new JSONObject();
        msg.put("table", "task_config");
        msg.put("type", "INSERT");
        JSONObject row = new JSONObject();
        row.put("taskId", 12345);
        msg.put("data", JSON.parseArray("[" + row.toJSONString() + "]"));

        consumer.handleMessage(msg.toJSONString(), ack);

        verify(taskConfigService, times(1)).invalidateTaskConfig(12345L);
        verify(taskConfigService, times(1)).refreshTaskConfig(12345L);
        verify(ack, times(1)).acknowledge();
    }

    @Test
    public void handleMessage_shouldSkipNonTaskTable_andAck() {
        JSONObject msg = new JSONObject();
        msg.put("table", "other_table");
        msg.put("type", "INSERT");
        JSONObject row = new JSONObject();
        row.put("taskId", 555);
        msg.put("data", JSON.parseArray("[" + row.toJSONString() + "]"));

        consumer.handleMessage(msg.toJSONString(), ack);

        verify(taskConfigService, never()).invalidateTaskConfig(anyLong());
        verify(taskConfigService, never()).refreshTaskConfig(anyLong());
        verify(ack, times(1)).acknowledge();
    }

    @Test
    public void handleMessage_shouldHandleDelete_usingOldArray() {
        JSONObject msg = new JSONObject();
        msg.put("table", "task_config");
        msg.put("type", "DELETE");
        JSONObject row = new JSONObject();
        row.put("task_id", "7777");
        msg.put("old", JSON.parseArray("[" + row.toJSONString() + "]"));

        consumer.handleMessage(msg.toJSONString(), ack);

        verify(taskConfigService, times(1)).invalidateTaskConfig(7777L);
        verify(taskConfigService, times(1)).refreshTaskConfig(7777L);
        verify(ack, times(1)).acknowledge();
    }

    @Test
    public void handleMessage_shouldNotFailOnMalformedJson_andAck() {
        String bad = "not-a-json";
        consumer.handleMessage(bad, ack);
        // should ack even on failure
        verify(ack, times(1)).acknowledge();
        // no service calls
        verify(taskConfigService, never()).invalidateTaskConfig(anyLong());
    }

    @Test
    public void extractTaskId_handles_various_field_names() throws Exception {
        // use reflection to call private method extractTaskId
        java.lang.reflect.Method m = TaskConfigChangeConsumer.class.getDeclaredMethod("extractTaskId", com.alibaba.fastjson.JSONObject.class);
        m.setAccessible(true);

        com.alibaba.fastjson.JSONObject row1 = new com.alibaba.fastjson.JSONObject(); row1.put("taskId", 123);
        Long r1 = (Long) m.invoke(consumer, row1);
        assertEquals(123L, r1.longValue());

        com.alibaba.fastjson.JSONObject row2 = new com.alibaba.fastjson.JSONObject(); row2.put("task_id", "456");
        Long r2 = (Long) m.invoke(consumer, row2);
        assertEquals(456L, r2.longValue());

        com.alibaba.fastjson.JSONObject row3 = new com.alibaba.fastjson.JSONObject(); row3.put("id", new java.math.BigDecimal("789"));
        Long r3 = (Long) m.invoke(consumer, row3);
        assertEquals(789L, r3.longValue());

        com.alibaba.fastjson.JSONObject row4 = new com.alibaba.fastjson.JSONObject(); row4.put("unknown", "x");
        Long r4 = (Long) m.invoke(consumer, row4);
        assertNull(r4);
    }
}
