package com.whu.graduation.taskincentive.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.util.Date;

/**
 * MyBatis-Plus 自动填充处理器：在插入/更新时为常见时间字段填充值
 */
@Component
public class MyMetaObjectHandler implements MetaObjectHandler {

    @Override
    public void insertFill(MetaObject metaObject) {
        try {
            if (metaObject.hasGetter("createTime")) {
                Object createTime = getFieldValByName("createTime", metaObject);
                if (createTime == null) {
                    setFieldValByName("createTime", new Date(), metaObject);
                }
            }
            if (metaObject.hasGetter("updateTime")) {
                Object updateTime = getFieldValByName("updateTime", metaObject);
                if (updateTime == null) {
                    setFieldValByName("updateTime", new Date(), metaObject);
                }
            }
            if (metaObject.hasGetter("acquireTime")) {
                Object acquireTime = getFieldValByName("acquireTime", metaObject);
                if (acquireTime == null) {
                    setFieldValByName("acquireTime", new Date(), metaObject);
                }
            }
        } catch (Exception e) {
            // 容错：若某些实体没有这些字段，忽略异常
        }
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        try {
            if (metaObject.hasGetter("updateTime")) {
                setFieldValByName("updateTime", new Date(), metaObject);
            }
        } catch (Exception e) {
            // 忽略
        }
    }
}
