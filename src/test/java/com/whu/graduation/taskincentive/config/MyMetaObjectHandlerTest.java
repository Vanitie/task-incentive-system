package com.whu.graduation.taskincentive.config;

import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.SystemMetaObject;
import org.junit.jupiter.api.Test;

import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MyMetaObjectHandlerTest {

    private final MyMetaObjectHandler handler = new MyMetaObjectHandler();

    static class TimeEntity {
        private Date createTime;
        private Date updateTime;
        private Date acquireTime;
    }

    @Test
    void insertFill_shouldPopulateNullTimeFields() {
        TimeEntity entity = new TimeEntity();
        MetaObject metaObject = SystemMetaObject.forObject(entity);

        handler.insertFill(metaObject);

        assertNotNull(entity.createTime);
        assertNotNull(entity.updateTime);
        assertNotNull(entity.acquireTime);
    }

    @Test
    void insertFill_shouldNotOverrideExistingValues() {
        TimeEntity entity = new TimeEntity();
        Date fixed = new Date(1000L);
        entity.createTime = fixed;
        entity.updateTime = fixed;
        entity.acquireTime = fixed;
        MetaObject metaObject = SystemMetaObject.forObject(entity);

        handler.insertFill(metaObject);

        assertSame(fixed, entity.createTime);
        assertSame(fixed, entity.updateTime);
        assertSame(fixed, entity.acquireTime);
    }

    @Test
    void updateFill_shouldSetUpdateTime() {
        TimeEntity entity = new TimeEntity();
        MetaObject metaObject = SystemMetaObject.forObject(entity);

        handler.updateFill(metaObject);

        assertNotNull(entity.updateTime);
    }

    @Test
    void methods_shouldSwallowUnexpectedMetaObjectErrors() {
        MetaObject metaObject = mock(MetaObject.class);
        when(metaObject.hasGetter("createTime")).thenThrow(new RuntimeException("boom"));

        assertDoesNotThrow(() -> handler.insertFill(metaObject));
        assertDoesNotThrow(() -> handler.updateFill(metaObject));
    }
}

