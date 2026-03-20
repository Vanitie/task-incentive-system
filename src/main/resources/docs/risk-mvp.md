# 风控子系统 MVP

## 目标与边界
- 防滥用：重放、频控、异常设备/IP。 
- 防超发：配额与预算控制。 
- 可追溯：决策日志可审计。 
- 低侵入：在任务达标后、扣库存前进行风控评估。

## 接口列表
- POST `/api/risk/decision/evaluate`
- GET `/api/risk/rules`
- POST `/api/risk/rules`
- PUT `/api/risk/rules/{id}`
- POST `/api/risk/rules/{id}/publish`
- POST `/api/risk/rules/{id}/rollback`
- POST `/api/risk/rules/validate`
- GET `/api/risk/blacklist`
- POST `/api/risk/blacklist`
- GET `/api/risk/whitelist`
- POST `/api/risk/whitelist`
- GET `/api/risk/quotas`
- PUT `/api/risk/quotas`
- GET `/api/risk/decisions`
- GET `/api/risk/dashboard/overview`
- GET `/api/risk/dashboard/daily-trend`

## 规则表达式示例
使用 SpEL 语法，变量来自风控上下文：
- `#count_1m`、`#count_1h`、`#amount_1d`、`#distinct_device_1d`
- `#ip_count_1m`、`#device_count_1m`
- `#userId`、`#taskId`、`#eventType`、`#amount`
- `#eventTime`

示例：
- 高频刷行为：`#count_1m > 20`
- 设备异常：`#distinct_device_1d > 3`
- IP 聚集：`#ip_count_1m > 50`
- 可疑首日激增：`#count_1h > 200`

## 表达式校验
- 创建/更新规则时，会先校验表达式语法与变量前缀（必须使用 `#变量名`）。
- 可通过 `/api/risk/rules/validate` 提前校验表达式。

## 规则生效时间
- `start_time` 与 `end_time` 可限制规则生效窗口，事件时间落在窗口外时不参与评估。

## 决策动作
- PASS：放行
- REJECT：拒绝
- DEGRADE_PASS：降级发奖（actionParams 中可设置 `{ "ratio": 0.5 }`）
- REVIEW：人工复核
- FREEZE：冻结奖励

## 主链路接入点
任务达标后、扣库存前：
1. 构建 `RiskDecisionRequest`
2. 调用 `riskDecisionService.evaluate`
3. 按决策执行发奖/拒绝/冻结

## 数据表
见 `docs/risk-schema.sql`

## 风控相关接口详细定义（补全所有接口）

### 1. 风控实时决策接口
- POST `/api/risk/decision/evaluate`
- 请求体：
  ```json
  {
    "requestId": "唯一请求ID",
    "eventId": "事件ID",
    "userId": 123456,
    "taskId": 7890,
    "eventType": "USER_SIGN",
    "eventTime": "2026-03-20T10:00:00",
    "amount": 10,
    "deviceId": "device-001",
    "ip": "192.168.1.1",
    "channel": "web",
    "ext": { "extraField": "value" }
  }
  ```
- 返回体：
  ```json
  {
    "code": 0,
    "msg": "ok",
    "data": {
      "decision": "PASS", // 决策结果
      "reasonCode": "NORMAL", // 原因码
      "hitRules": [
        {
          "ruleId": 1,
          "ruleName": "高频刷",
          "ruleType": "频控",
          "action": "REJECT",
          "actionParams": "{}",
          "reasonCode": "FREQ_LIMIT"
        }
      ],
      "riskScore": 10,
      "traceId": "trace-001",
      "degradeRatio": 0.5 // 降级比例，仅 DEGRADE_PASS 有值
    }
  }
  ```

### 2. 风控规则管理接口
- GET `/api/risk/rules?page=1&size=20`
- 返回体：
  ```json
  {
    "code": 0,
    "msg": "ok",
    "data": {
      "total": 100,
      "page": 1,
      "size": 20,
      "items": [
        {
          "id": 1,
          "name": "高频刷",
          "type": "频控",
          "priority": 10,
          "status": 1,
          "conditionExpr": "#count_1m > 20",
          "action": "REJECT",
          "actionParams": "{}",
          "version": 1,
          "createdBy": "admin",
          "updatedBy": "admin",
          "startTime": "2026-03-01T00:00:00",
          "endTime": "2026-03-31T23:59:59",
          "createdAt": "2026-03-01T10:00:00",
          "updatedAt": "2026-03-01T10:00:00"
        }
      ]
    }
  }
  ```

- POST `/api/risk/rules`
- 请求体：
  ```json
  {
    "name": "高频刷",
    "type": "频控",
    "priority": 10,
    "status": 1,
    "conditionExpr": "#count_1m > 20",
    "action": "REJECT",
    "actionParams": "{}",
    "startTime": "2026-03-01T00:00:00",
    "endTime": "2026-03-31T23:59:59",
    "createdBy": "admin",
    "updatedBy": "admin"
  }
  ```
- 返回体同上。

- PUT `/api/risk/rules/{id}`
- 请求体：同 POST `/api/risk/rules`，仅需补充 id 字段。
- 返回体同上。

- POST `/api/risk/rules/{id}/publish`
- 请求体：
  ```json
  {
    "version": 2
  }
  ```
- 返回体：
  ```json
  {
    "code": 0,
    "msg": "ok",
    "data": {
      "ruleId": 1,
      "version": 2,
      "status": "published"
    }
  }
  ```

- POST `/api/risk/rules/{id}/rollback`
- 请求体：
  ```json
  {
    "version": 1
  }
  ```
- 返回体同上。

- POST `/api/risk/rules/validate`
- 请求体：
  ```json
  {
    "conditionExpr": "#count_1m > 20"
  }
  ```
- 返回体：
  ```json
  {
    "code": 0,
    "msg": "ok",
    "data": {
      "valid": true,
      "message": "表达式校验通过",
      "missingSharpVariables": []
    }
  }
  ```

### 3. 黑名单管理接口
- GET `/api/risk/blacklist?page=1&size=20`
- 返回体：
  ```json
  {
    "code": 0,
    "msg": "ok",
    "data": {
      "total": 10,
      "page": 1,
      "size": 20,
      "items": [
        {
          "id": 1,
          "targetType": "user",
          "targetValue": "10001",
          "source": "admin",
          "expireAt": "2026-03-31T23:59:59",
          "status": 1,
          "createdAt": "2026-03-01T10:00:00"
        }
      ]
    }
  }
  ```

- POST `/api/risk/blacklist`
- 请求体：
  ```json
  {
    "targetType": "user",
    "targetValue": "10001",
    "source": "admin",
    "expireAt": "2026-03-31T23:59:59"
  }
  ```
- 返回体同上。

### 4. 白名单管理接口
- GET `/api/risk/whitelist?page=1&size=20`
- 返回体结构同黑名单。

- POST `/api/risk/whitelist`
- 请求体：
  ```json
  {
    "targetType": "user",
    "targetValue": "10001",
    "source": "admin",
    "expireAt": "2026-03-31T23:59:59"
  }
  ```
- 返回体同上。

### 5. 配额管理接口
- GET `/api/risk/quotas?scopeType=user&scopeId=10001&periodType=day`
- 返回体：
  ```json
  {
    "code": 0,
    "msg": "ok",
    "data": [
      {
        "id": 1,
        "scopeType": "user",
        "scopeId": "10001",
        "periodType": "day",
        "limitValue": 100,
        "usedValue": 20,
        "resetAt": "2026-03-21T00:00:00"
      }
    ]
  }
  ```

- PUT `/api/risk/quotas`
- 请求体：
  ```json
  {
    "id": 1,
    "usedValue": 30
  }
  ```
- 返回体同上。

### 6. 决策日志查询接口
- GET `/api/risk/decisions?pageNo=1&pageSize=20&taskId=7890&decision=PASS&start=2026-03-01T00:00:00&end=2026-03-20T23:59:59`
- 返回体：
  ```json
  {
    "code": 0,
    "msg": "ok",
    "data": {
      "total": 100,
      "page": 1,
      "size": 20,
      "items": [
        {
          "id": 1,
          "requestId": "req-001",
          "eventId": "evt-001",
          "userId": 10001,
          "taskId": 7890,
          "decision": "PASS",
          "reasonCode": "NORMAL",
          "hitRules": [
            {
              "ruleId": 1,
              "ruleName": "高频刷",
              "action": "REJECT"
            }
          ],
          "riskScore": 10,
          "latencyMs": 20,
          "createdAt": "2026-03-20T10:00:00"
        }
      ]
    }
  }
  ```

### 7. 风控看板总览接口
- GET `/api/risk/dashboard/overview?start=2026-03-01T00:00:00&end=2026-03-20T23:59:59`
- 返回体：
  ```json
  {
    "code": 0,
    "msg": "ok",
    "data": {
      "start": "2026-03-01T00:00:00",
      "end": "2026-03-20T23:59:59",
      "total": 1000,
      "pass": 800,
      "reject": 100,
      "degradePass": 50,
      "review": 30,
      "freeze": 20,
      "interceptRate": 0.15,
      "passRate": 0.8,
      "avgLatencyMs": 20.5,
      "p95LatencyMs": 40.0,
      "p99LatencyMs": 60.0
    }
  }
  ```

### 8. 风控看板趋势接口
- GET `/api/risk/dashboard/daily-trend?start=2026-03-01T00:00:00&end=2026-03-20T23:59:59`
- 返回体：
  ```json
  {
    "code": 0,
    "msg": "ok",
    "data": [
      {
        "date": "2026-03-01",
        "total": 100,
        "pass": 80,
        "reject": 10,
        "degradePass": 5,
        "review": 3,
        "freeze": 2
      }
      // ...更多日期
    ]
  }
  ```
