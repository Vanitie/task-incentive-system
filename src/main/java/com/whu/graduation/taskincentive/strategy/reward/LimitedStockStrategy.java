package com.whu.graduation.taskincentive.strategy.reward;

import com.whu.graduation.taskincentive.common.enums.StockType;
import com.whu.graduation.taskincentive.service.TaskStockService;
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

    @Autowired(required = false)
    private TaskStockService taskStockService;

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

        Long result = null;
        try {
            result = redisTemplate.execute(
                    luaScript,
                    Collections.singletonList(key)
            );
        } catch (Exception e) {
            log.warn("redis lua exec failed for key={}, err={}", key, e.getMessage());
        }

        if (result == null) {
            // redis不可用，直接回退到DB扣库存（如果 service 可用）
            if (taskStockService != null) {
                return taskStockService.deductStock(rewardId, 1);
            }
            return false;
        }

        if (result > 0) return true;
        if (result == 0) return false;

        // -1意味着 shard 尚未初始化，可能是第一次访问或者之前的初始化失败了；尝试从DB加载总库存并平均分配到各个 shard 中
        if (result == -1L) {
            if (taskStockService != null) {
                // 从DB查询总库存，并平均分配到各个 shard 中；如果 DB 中库存不足，则不初始化
                try {
                    var stock = taskStockService.getById(rewardId);
                    if (stock != null && stock.getAvailableStock() != null && stock.getAvailableStock() > 0) {
                        int perShard = Math.max(1, stock.getAvailableStock() / SHARD_COUNT);
                        for (int i = 0; i < SHARD_COUNT; i++) {
                            String k = "reward:stock:" + rewardId + ":" + i;
                            try { redisTemplate.opsForValue().setIfAbsent(k, String.valueOf(perShard)); } catch (Exception ignore) {}
                        }
                        // 重试一次
                        try {
                            Long retry = redisTemplate.execute(luaScript, Collections.singletonList(key));
                            return retry != null && retry > 0;
                        } catch (Exception e) {
                            log.warn("redis lua retry failed for key={}, err={}", key, e.getMessage());
                            return false;
                        }
                    }
                } catch (Exception e) {
                    log.warn("failed to init shards from DB for rewardId={}, err={}", rewardId, e.getMessage());
                }
            }
        }

        return false;
    }
}
