package com.whu.graduation.taskincentive.strategy.reward;

import com.whu.graduation.taskincentive.common.enums.StockType;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 限量策略
 */
@Slf4j
@Service
public class LimitedStockStrategy implements StockStrategy {

    private static final int SHARD_COUNT = 8;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    private DefaultRedisScript<Long> luaScript;

    @PostConstruct
    public void init() {

        luaScript = new DefaultRedisScript<>();

        luaScript.setResultType(Long.class);

        luaScript.setScriptText(
                "local stock = redis.call('GET', KEYS[1]) " +
                        "if not stock then return -1 end " +
                        "if tonumber(stock) <= 0 then return 0 end " +
                        "redis.call('DECR', KEYS[1]) return 1"
        );
    }

    @Override
    public StockType getType() {
        return StockType.LIMITED;
    }

    @Override
    public boolean acquireStock(Long rewardId) {

        int shard = ThreadLocalRandom.current().nextInt(SHARD_COUNT);

        String key = "reward:stock:" + rewardId + ":" + shard;

        Long result = redisTemplate.execute(
                luaScript,
                Collections.singletonList(key)
        );

        return result != null && result > 0;
    }
}
