# 启动参数与业务自定义配置

本文档汇总本项目常用的“业务相关”启动参数，重点覆盖缓存预热模式（warmup）以及压测/生产调优时常用开关。

## 1. Cache Warmup 模式（新增）

`CacheWarmupRunner` 支持通过启动参数或配置切换 4 种预热策略。

- 命令行优先级最高：`--app.cache-warmup.mode=...`
- 别名：`--cache-warmup-mode=...`、`--warmup-mode=...`
- 若未传命令行参数，则读取 `app.cache-warmup.mode`
- 若 `app.cache-warmup.enabled=false`，且未传命令行 mode，则不执行预热

### 1.1 模式说明

| mode | 含义 | 执行内容 |
|---|---|---|
| `off` | 不执行预热 | 跳过 risk/taskConfig/hotUser 全部阶段 |
| `memory_only` | 仅预热内存 | 预热 risk 内存缓存 + taskConfig 本地缓存（不写 Redis） |
| `memory_and_redis_limited` | 全量内存 + 有限 Redis | risk 内存 + taskConfig 本地/Redis + hotUser Redis（使用 limited 限额） |
| `full` | 全量内存 + 扩展 Redis | risk 内存 + taskConfig 本地/Redis + hotUser Redis（使用 full 限额） |

### 1.2 相关配置项

```yaml
app:
  cache-warmup:
    mode: memory_and_redis_limited
    enabled: true
    fail-fast: false
    max-duration-seconds: 90

    task-config-batch-size: 500
    task-config-redis-ttl-seconds: 3600

    # limited 模式
    hot-user-limit: 1000
    instances-per-hot-user: 30
    max-total-hot-user-instances: 30000
    hot-user-batch-size: 200

    # full 模式
    full-hot-user-limit: 20000
    full-instances-per-hot-user: 120
    full-max-total-hot-user-instances: 2000000
    full-hot-user-batch-size: 500

    user-task-redis-ttl-minutes: 120
```

### 1.3 启动示例

```powershell
# 1) 完全关闭预热
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.arguments=--cache-warmup-mode=off"

# 2) 仅预热内存
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.arguments=--app.cache-warmup.mode=memory_only"

# 3) 全量内存 + 有限 Redis（推荐单机压测默认）
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.arguments=--app.cache-warmup.mode=memory_and_redis_limited"

# 4) 全量内存 + full Redis 预热
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.arguments=--app.cache-warmup.mode=full"
```

---

## 2. 其他业务自定义启动参数（整合）

以下参数均来自当前项目代码/配置（`application.yaml` + `AppProperties` + `@Value` 注入）。

### 2.1 鉴权与安全

| 配置项 | 默认值/示例 | 说明 |
|---|---|---|
| `app.security.admin.username` | `admin` | 管理端默认账号 |
| `app.security.admin.password` | `admin123` | 管理端默认密码 |
| `app.security.jwt.secret` | （建议显式配置） | JWT 签名密钥 |
| `app.security.jwt.expiration-ms` | `86400000` | JWT 过期时间（毫秒） |

### 2.2 异步补偿与 DLQ

| 配置项 | 默认值 | 说明 |
|---|---|---|
| `app.async-compensation.dlq-on-kafka-failure` | `true` | Kafka 异步发送失败时是否写入 DLQ |
| `app.dlq.topic` | `dlq-topic` | DLQ topic 名称 |

### 2.3 主链路日志控制

| 配置项 | 默认值 | 说明 |
|---|---|---|
| `app.log-control.main-path-enabled` | `true` | 是否输出主链路高频 info/warn 日志 |
| `APP_MAIN_PATH_LOG_ENABLED` | `true` | 上述配置项对应环境变量 |

### 2.4 任务引擎线程池

| 配置项 | 默认值 | 对应环境变量 |
|---|---:|---|
| `app.executors.task-engine-event.core-pool-size` | `64` | `APP_TASK_ENGINE_EXECUTOR_CORE` |
| `app.executors.task-engine-event.max-pool-size` | `256` | `APP_TASK_ENGINE_EXECUTOR_MAX` |
| `app.executors.task-engine-event.queue-capacity` | `8000` | `APP_TASK_ENGINE_EXECUTOR_QUEUE` |
| `app.executors.task-engine-event.keep-alive-seconds` | `60` | （无） |
| `app.executors.task-engine-event.thread-name-prefix` | `task-engine-event-` | （无） |

### 2.5 数据库连接池（压测常调）

| 配置项 | 对应环境变量 |
|---|---|
| `spring.datasource.hikari.minimum-idle` | `APP_HIKARI_MIN_IDLE` |
| `spring.datasource.hikari.maximum-pool-size` | `APP_HIKARI_MAX_POOL_SIZE` |
| `spring.datasource.hikari.connection-timeout` | `APP_HIKARI_CONNECTION_TIMEOUT_MS` |
| `spring.datasource.hikari.validation-timeout` | `APP_HIKARI_VALIDATION_TIMEOUT_MS` |
| `spring.datasource.hikari.idle-timeout` | `APP_HIKARI_IDLE_TIMEOUT_MS` |
| `spring.datasource.hikari.max-lifetime` | `APP_HIKARI_MAX_LIFETIME_MS` |
| `spring.datasource.hikari.leak-detection-threshold` | `APP_HIKARI_LEAK_DETECTION_MS` |
| `spring.datasource.hikari.keepalive-time` | `APP_HIKARI_KEEPALIVE_MS` |

### 2.6 日志文件与级别（压测建议）

| 配置项 | 对应环境变量 |
|---|---|
| `logging.file.name` | `APP_LOG_FILE` |
| `logging.level.root` | `APP_ROOT_LOG_LEVEL` |
| `logging.level.com.whu.graduation.taskincentive` | `APP_LOG_LEVEL` |
| `logging.logback.rollingpolicy.file-name-pattern` | `APP_LOG_FILE_PATTERN` |

---

## 3. 建议实践

1. 单机 4000/6000QPS 压测优先使用 `memory_and_redis_limited`，避免启动即把 Redis/DB 压到 full。
2. 需要验证极限场景时再切换 `full`，并同时提高 Redis/MySQL 资源与监控粒度。
3. 若仅做链路可用性验证，使用 `memory_only` 可显著缩短启动预热时间。
4. 如需完全禁用预热，使用 `off`（或配置 `app.cache-warmup.enabled=false`）。

