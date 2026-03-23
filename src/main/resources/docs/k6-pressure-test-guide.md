# Engine ToC 接口压测功能说明（k6）

本文档说明当前 `engine_process_event_k6.js` 已支持的压测能力、参数和典型用法。

## 1. 当前支持的压测功能

- 单接口恒定速率压测（`baseline`）
  - 可压同步或异步接口（由 `TARGET_MODE` 控制）
  - 使用 `constant-arrival-rate`，以目标 QPS 持续打流
- 同步/异步并行对比压测（`compare`）
  - 同时压 `process-event-async` 和 `process-event-sync`
  - 总速率由 `RATE` 指定，脚本自动均分给两个场景
- 极限 QPS 爬坡压测（`max`）
  - 使用 `ramping-arrival-rate` 逐级升压
  - 默认已配置从 `1000 -> 10000` QPS（每级 1 分钟）

## 2. 支持压测的接口

- 异步接口：`/api/engine/process-event-async`
- 同步接口：`/api/engine/process-event-sync`

脚本会按模式自动路由请求：

- `baseline`：根据 `TARGET_MODE` 选择同步或异步
- `compare`：两个接口同时压
- `max`：默认按 `TARGET_MODE` 选择（通常建议先压异步）

## 3. 内置流量特征（仿真能力）

脚本已内置以下行为，用于更接近真实流量：

- 用户池随机轮转：使用造数生成的 `USER_IDS`
- 事件类型随机：`USER_LEARN` / `USER_SIGN`
- 事件值随机：`value=1~5`
- 设备与 IP 维度模拟：`deviceId`、`ip` 随用户派生
- 幂等/重放仿真：按 `DUPLICATE_RATE` 注入重复 `messageId`
- 缺失幂等键仿真：按 `NO_MSG_ID_RATE` 随机不传 `messageId`
- 可选鉴权：支持 `BEARER_TOKEN`

## 4. 已支持的环境变量

### 4.0 ID 语义（重要）

- `requestId`：每次请求必须唯一；脚本默认已为每一条请求生成唯一值
- `messageId`：用于消息去重仿真，可按 `DUPLICATE_RATE` 注入重复值
- 当重复 `messageId` 命中去重时，接口返回 `status=duplicate`

### 4.1 场景与速率相关

- `TEST_MODE`
  - `baseline`（默认）
  - `compare`
  - `max`
- `TARGET_MODE`
  - `async`（默认）
  - `sync`
- `RATE`
  - `baseline/compare` 总 QPS，默认 `200`
- `DURATION`
  - 稳态压测时长，默认 `3m`
- `START_RATE`
  - `max` 起始 QPS，默认 `1000`
- `MAX_STAGES_JSON`
  - `max` 爬坡阶段，JSON 数组
  - 默认：`1000~10000`，每级 `1m`
- `PRE_VUS`
  - 预分配 VU，默认 `800`
- `MAX_VUS`
  - 最大 VU，默认 `12000`

### 4.2 请求与数据相关

- `BASE_URL`
  - 后端地址，默认 `http://127.0.0.1:8080`
- `BEARER_TOKEN`
  - 可选，设置后自动加 `Authorization: Bearer xxx`
- `DUPLICATE_RATE`
  - 重复消息比例，默认 `0.1`
- `NO_MSG_ID_RATE`
  - 缺失 `messageId` 比例，默认 `0.03`
- `SLEEP_SEC`
  - 每轮请求后额外 sleep 秒数，默认 `0`

## 5. 结果统计与阈值

脚本当前内置阈值：

- `http_req_failed: rate < 0.01`
- `http_req_duration: p(95) < 500ms, p(99) < 1200ms`

输出：

- 控制台实时统计
- 结束后生成 `k6-summary.json`

请求会带标签，便于分场景观察：

- `endpoint_type`: `sync` / `async`
- `scenario_name`: 具体场景名（如 `compare_async`）

## 6. 典型运行方式

### 6.1 单接口稳态压测（异步）

```powershell
$env:BASE_URL="http://<后端IP>:8080"
$env:BEARER_TOKEN="<token>"
$env:TEST_MODE="baseline"
$env:TARGET_MODE="async"
$env:RATE="2000"
$env:DURATION="5m"
k6 run .\engine_process_event_k6.js
```

### 6.2 同步/异步并行对比

```powershell
$env:BASE_URL="http://<后端IP>:8080"
$env:BEARER_TOKEN="<token>"
$env:TEST_MODE="compare"
$env:RATE="4000"
$env:DURATION="5m"
k6 run .\engine_process_event_k6.js
```

说明：该模式下同步和异步各承担约一半流量。

### 6.3 极限 QPS 爬坡到 10k

```powershell
$env:BASE_URL="http://<后端IP>:8080"
$env:BEARER_TOKEN="<token>"
$env:TEST_MODE="max"
$env:TARGET_MODE="async"
$env:START_RATE="1000"
$env:PRE_VUS="1500"
$env:MAX_VUS="20000"
k6 run .\engine_process_event_k6.js
```

## 7. 当前功能边界（已知）

- 当前脚本以 HTTP 层指标为主，不直接输出业务成功率（需结合后端日志/监控）
- 未内置自动二分查找“最大稳定 QPS”，需人工根据失败率与延迟拐点判断
- 若网络环境有客户端隔离（同 Wi-Fi 但不互通），需先解决网络连通性

## 8. 建议压测流程

1. 先 `baseline` 做 500/1000/2000 QPS 稳态摸底
2. 再 `compare` 直接看同步 vs 异步性能差距
3. 最后 `max` 爬坡定位极限 QPS 与拐点
4. 结合后端 CPU、GC、数据库、消息队列指标做瓶颈归因

