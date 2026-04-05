package com.whu.graduation.taskincentive.mq;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.whu.graduation.taskincentive.config.InstanceInfo;
import com.whu.graduation.taskincentive.service.TaskConfigService;
import com.whu.graduation.taskincentive.testutil.ReflectionTestUtils;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.ConcurrentMessageListenerContainer;
import org.springframework.kafka.support.Acknowledgment;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

/**
 * 单元测试 TaskConfigChangeConsumer 的核心处理逻辑（不依赖 Kafka/Redis/Docker）。
 */
public class TaskConfigChangeConsumerUnitTest {

    private TaskConfigChangeConsumer consumer;
    private TaskConfigService taskConfigService;
    private InstanceInfo instanceInfo;
    private Acknowledgment ack;
    private StringRedisTemplate stringRedisTemplate;
    private ValueOperations<String, String> valueOperations;

    @BeforeEach
    public void setup() {
        taskConfigService = mock(TaskConfigService.class);
        instanceInfo = mock(InstanceInfo.class);
        when(instanceInfo.getInstanceId()).thenReturn("unit-test-instance");
        ack = mock(Acknowledgment.class);
        stringRedisTemplate = mock(StringRedisTemplate.class);
        valueOperations = mock(ValueOperations.class);
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);

        consumer = new TaskConfigChangeConsumer();
        ReflectionTestUtils.setFieldRecursively(consumer, "taskConfigService", taskConfigService);
        ReflectionTestUtils.setFieldRecursively(consumer, "instanceInfo", instanceInfo);
        ReflectionTestUtils.setFieldRecursively(consumer, "stringRedisTemplate", stringRedisTemplate);
        ReflectionTestUtils.setFieldRecursively(consumer, "topic", "task-config-change");
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
    public void handleMessage_shouldSkipWhenTableMissing_andAck() {
        JSONObject msg = new JSONObject();
        msg.put("type", "INSERT");

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
    public void handleMessage_shouldNotThrowOnMalformedJson_whenAckIsNull() {
        assertDoesNotThrow(() -> consumer.handleMessage("not-a-json", null));
    }

    @Test
    public void handleMessage_shouldProcessWhenAckIsNull() {
        JSONObject msg = new JSONObject();
        msg.put("table", "task_config");
        msg.put("type", "UPDATE");
        JSONObject row = new JSONObject();
        row.put("id", "1001");
        msg.put("data", JSON.parseArray("[" + row.toJSONString() + "]"));

        assertDoesNotThrow(() -> consumer.handleMessage(msg.toJSONString(), null));
        verify(taskConfigService).invalidateTaskConfig(1001L);
        verify(taskConfigService).refreshTaskConfig(1001L);
    }

    @Test
    public void handleMessage_shouldIgnoreAckErrorWhenSkippingOtherTable() {
        doThrow(new RuntimeException("ack-fail")).when(ack).acknowledge();
        JSONObject msg = new JSONObject();
        msg.put("table", "other_table");
        msg.put("type", "INSERT");

        assertDoesNotThrow(() -> consumer.handleMessage(msg.toJSONString(), ack));
        verify(taskConfigService, never()).refreshTaskConfig(anyLong());
    }

    @Test
    public void handleMessage_shouldAckWhenNoTaskIdExtracted() {
        JSONObject msg = new JSONObject();
        msg.put("table", "task_config");
        msg.put("type", "UPDATE");
        JSONObject row = new JSONObject();
        row.put("name", "only-text");
        msg.put("data", JSON.parseArray("[" + row.toJSONString() + "]"));

        consumer.handleMessage(msg.toJSONString(), ack);

        verify(taskConfigService, never()).refreshTaskConfig(anyLong());
        verify(ack).acknowledge();
    }

    @Test
    public void handleMessage_shouldContinueWhenSingleTaskRefreshFails() {
        JSONObject msg = new JSONObject();
        msg.put("table", "task_config");
        msg.put("type", "UPDATE");
        JSONObject row1 = new JSONObject();
        row1.put("taskId", 11);
        JSONObject row2 = new JSONObject();
        row2.put("taskId", 12);
        msg.put("data", JSON.parseArray("[" + row1.toJSONString() + "," + row2.toJSONString() + "]"));

        doThrow(new RuntimeException("db fail")).when(taskConfigService).refreshTaskConfig(11L);

        consumer.handleMessage(msg.toJSONString(), ack);

        verify(taskConfigService).refreshTaskConfig(11L);
        verify(taskConfigService).refreshTaskConfig(12L);
        verify(ack).acknowledge();
    }

    @Test
    public void extractTaskId_shouldUseFirstNumericFallbackField() throws Exception {
        java.lang.reflect.Method m = TaskConfigChangeConsumer.class.getDeclaredMethod("extractTaskId", com.alibaba.fastjson.JSONObject.class);
        m.setAccessible(true);

        com.alibaba.fastjson.JSONObject row = new com.alibaba.fastjson.JSONObject();
        row.put("foo", "12.8");
        row.put("bar", "x");
        Long out = (Long) m.invoke(consumer, row);

        assertEquals(12L, out.longValue());
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

    @Test
    public void handleMessage_deleteWithEmptyOld_shouldFallbackToData() {
        JSONObject msg = new JSONObject();
        msg.put("table", "task_config");
        msg.put("type", "DELETE");
        msg.put("old", JSON.parseArray("[]"));
        JSONObject row = new JSONObject();
        row.put("id", 8899);
        msg.put("data", JSON.parseArray("[" + row.toJSONString() + "]"));

        consumer.handleMessage(msg.toJSONString(), ack);

        verify(taskConfigService).invalidateTaskConfig(8899L);
        verify(taskConfigService).refreshTaskConfig(8899L);
        verify(ack).acknowledge();
    }

    @Test
    public void handleMessage_deleteWithNullOld_shouldFallbackToData() {
        JSONObject msg = new JSONObject();
        msg.put("table", "task_config");
        msg.put("type", "DELETE");
        JSONObject row = new JSONObject();
        row.put("id", 9901);
        msg.put("data", JSON.parseArray("[" + row.toJSONString() + "]"));

        consumer.handleMessage(msg.toJSONString(), ack);

        verify(taskConfigService).invalidateTaskConfig(9901L);
        verify(taskConfigService).refreshTaskConfig(9901L);
        verify(ack).acknowledge();
    }

    @Test
    public void handleMessage_updateWithNullData_shouldAckWithoutServiceCall() {
        JSONObject msg = new JSONObject();
        msg.put("table", "task_config");
        msg.put("type", "UPDATE");

        consumer.handleMessage(msg.toJSONString(), ack);

        verify(taskConfigService, never()).invalidateTaskConfig(anyLong());
        verify(taskConfigService, never()).refreshTaskConfig(anyLong());
        verify(ack).acknowledge();
    }

    @Test
    public void handleMessage_shouldIgnoreAckFailure_onCatchPath() {
        doThrow(new RuntimeException("ack-fail")).when(ack).acknowledge();
        assertDoesNotThrow(() -> consumer.handleMessage("not-json", ack));
    }

    @Test
    public void parseLongFlexible_shouldCoverNumberEmptyAndInvalid() throws Exception {
        java.lang.reflect.Method m = TaskConfigChangeConsumer.class.getDeclaredMethod("parseLongFlexible", Object.class);
        m.setAccessible(true);

        assertEquals(5L, ((Long) m.invoke(consumer, 5)).longValue());
        assertNull(m.invoke(consumer, ""));
        assertEquals(12L, ((Long) m.invoke(consumer, "12.99")).longValue());
        assertNull(m.invoke(consumer, "abc"));
    }

    @Test
    public void parseLongFlexible_shouldReturnNullForNullInput() throws Exception {
        java.lang.reflect.Method m = TaskConfigChangeConsumer.class.getDeclaredMethod("parseLongFlexible", Object.class);
        m.setAccessible(true);

        assertNull(m.invoke(consumer, new Object[]{null}));
    }

    @Test
    public void extractTaskId_shouldHandleNullRow_andFallbackWhenPrimaryFieldInvalid() throws Exception {
        java.lang.reflect.Method m = TaskConfigChangeConsumer.class.getDeclaredMethod("extractTaskId", com.alibaba.fastjson.JSONObject.class);
        m.setAccessible(true);

        assertNull(m.invoke(consumer, new Object[]{null}));

        com.alibaba.fastjson.JSONObject row1 = new com.alibaba.fastjson.JSONObject();
        row1.put("taskId", "bad");
        row1.put("task_id", "200");
        Long r1 = (Long) m.invoke(consumer, row1);
        assertEquals(200L, r1.longValue());

        com.alibaba.fastjson.JSONObject row2 = new com.alibaba.fastjson.JSONObject();
        row2.put("task_id", "bad");
        row2.put("id", "300");
        Long r2 = (Long) m.invoke(consumer, row2);
        assertEquals(300L, r2.longValue());

        com.alibaba.fastjson.JSONObject row3 = new com.alibaba.fastjson.JSONObject();
        row3.put("id", "bad");
        row3.put("fallback", "401");
        Long r3 = (Long) m.invoke(consumer, row3);
        assertEquals(401L, r3.longValue());
    }

    @Test
    public void handleMessage_shouldSkipNonTaskTable_whenAckIsNull() {
        JSONObject msg = new JSONObject();
        msg.put("table", "other_table");
        msg.put("type", "UPDATE");

        assertDoesNotThrow(() -> consumer.handleMessage(msg.toJSONString(), null));
        verify(taskConfigService, never()).invalidateTaskConfig(anyLong());
        verify(taskConfigService, never()).refreshTaskConfig(anyLong());
    }

    @Test
    public void handleMessage_shouldIgnoreNonNumericCreateTimestamp() {
        JSONObject msg = new JSONObject();
        msg.put("table", "task_config");
        msg.put("type", "UPDATE");
        JSONObject row = new JSONObject();
        row.put("taskId", 1301);
        msg.put("data", JSON.parseArray("[" + row.toJSONString() + "]"));
        when(valueOperations.get("task_config_create_time:1301")).thenReturn("not-a-number");

        consumer.handleMessage(msg.toJSONString(), ack);

        verify(taskConfigService).invalidateTaskConfig(1301L);
        verify(taskConfigService).refreshTaskConfig(1301L);
        verify(ack).acknowledge();
    }

    @Test
    public void handleMessage_shouldCoverListenerEquivalentPath_withConsumerRecordValue() {
        JSONObject msg = new JSONObject();
        msg.put("table", "task_config");
        msg.put("type", "UPDATE");
        JSONObject row = new JSONObject();
        row.put("taskId", 2001);
        msg.put("data", JSON.parseArray("[" + row.toJSONString() + "]"));

        Acknowledgment localAck = mock(Acknowledgment.class);
        ConsumerRecord<String, String> record = new ConsumerRecord<>("task-config-change", 0, 0L, "k", msg.toJSONString());

        consumer.handleMessage(record.value(), localAck);

        verify(taskConfigService).invalidateTaskConfig(2001L);
        verify(taskConfigService).refreshTaskConfig(2001L);
        verify(localAck).acknowledge();
    }

    @Test
    public void init_shouldWireListener_andListenerShouldDelegateToHandleMessage() {
        @SuppressWarnings("unchecked")
        ConcurrentKafkaListenerContainerFactory<String, String> factory = mock(ConcurrentKafkaListenerContainerFactory.class);
        @SuppressWarnings("unchecked")
        ConcurrentMessageListenerContainer<String, String> container = mock(ConcurrentMessageListenerContainer.class);
        ContainerProperties props = mock(ContainerProperties.class);

        when(factory.createContainer(eq("task-config-change"))).thenReturn(container);
        when(container.getContainerProperties()).thenReturn(props);

        final org.springframework.kafka.listener.AcknowledgingMessageListener<String, String>[] holder =
                new org.springframework.kafka.listener.AcknowledgingMessageListener[1];
        doAnswer(invocation -> {
            holder[0] = invocation.getArgument(0);
            return null;
        }).when(container).setupMessageListener(any());

        ReflectionTestUtils.setFieldRecursively(consumer, "factory", factory);

        assertThrows(NullPointerException.class, () -> consumer.init());

        verify(props).setGroupId("task-config-change-group-unit-test-instance");
        verify(props).setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
        verify(container).setConcurrency(1);
        assertNotNull(holder[0]);

        JSONObject msg = new JSONObject();
        msg.put("table", "task_config");
        msg.put("type", "UPDATE");
        JSONObject row = new JSONObject();
        row.put("taskId", 2101);
        msg.put("data", JSON.parseArray("[" + row.toJSONString() + "]"));

        Acknowledgment localAck = mock(Acknowledgment.class);
        holder[0].onMessage(new ConsumerRecord<>("task-config-change", 0, 0L, "k", msg.toJSONString()), localAck);

        verify(taskConfigService).invalidateTaskConfig(2101L);
        verify(taskConfigService).refreshTaskConfig(2101L);
        verify(localAck).acknowledge();
    }

    @Test
    public void shutdown_shouldSwallowContainerStopException() {
        @SuppressWarnings("unchecked")
        ConcurrentMessageListenerContainer<String, String> container = mock(ConcurrentMessageListenerContainer.class);
        ReflectionTestUtils.setFieldRecursively(consumer, "container", container);

        assertDoesNotThrow(() -> consumer.shutdown());
    }

    @Test
    public void shutdown_shouldDoNothing_whenContainerIsNull() {
        ReflectionTestUtils.setFieldRecursively(consumer, "container", null);
        assertDoesNotThrow(() -> consumer.shutdown());
    }

    @Test
    public void init_shouldStartContainerSuccessfully() {
        @SuppressWarnings("unchecked")
        ConcurrentKafkaListenerContainerFactory<String, String> localFactory = mock(ConcurrentKafkaListenerContainerFactory.class);
        @SuppressWarnings("unchecked")
        ConcurrentMessageListenerContainer<String, String> localContainer = mock(ConcurrentMessageListenerContainer.class);
        ContainerProperties props = mock(ContainerProperties.class);

        when(localFactory.createContainer(eq("task-config-change"))).thenReturn(localContainer);
        when(localContainer.getContainerProperties()).thenReturn(props);

        ReflectionTestUtils.setFieldRecursively(consumer, "factory", localFactory);

        // When the container is a pure mock, AbstractMessageListenerContainer#start may hit internal null state.
        // We still validate the wiring path before start is reached.
        assertThrows(NullPointerException.class, () -> consumer.init());

        verify(props).setGroupId("task-config-change-group-unit-test-instance");
        verify(props).setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
        verify(localContainer).setAutoStartup(true);
        verify(localContainer).setConcurrency(1);
    }

}
