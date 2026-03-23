# 演示数据生成工具说明

## 文件位置

- 旧版生成器：`src/main/java/com/whu/graduation/taskincentive/util/DemoSqlDataGenerator.java`
- 推荐生成器（引擎链路版）：`src/main/java/com/whu/graduation/taskincentive/util/EngineChainSqlGenerator.java`

## 引擎链路版覆盖范围

`EngineChainSqlGenerator` 按 `TaskEngine` 实际链路生成初始化 SQL，默认让用户和任务创建时间分布在最近 7 天，覆盖：

1. `user`
2. `badge`
3. `task_config`（`task_type` 使用 `ACCUMULATE/CONTINUOUS/STAIR/WINDOW_ACCUMULATE`）
4. `task_stock`（`stock_type=LIMITED` 时生成）
5. `user_task_instance`（状态包含 `ACCEPTED/IN_PROGRESS/COMPLETED/CANCELLED`）
6. `user_action_log`
7. `user_reward_record`
8. `user_badge`
9. `user.point_balance` 回写（与积分奖励一致）
10. `risk_whitelist`
11. `risk_blacklist`
12. `risk_quota`（含 `scope_id=ALL` 与具体 ID）
13. `risk_rule`（覆盖 `REJECT/REVIEW/FREEZE/DEGRADE_PASS`）

## 如何运行

在项目根目录执行：

```powershell
mvn -q -DskipTests compile
java -cp target/classes com.whu.graduation.taskincentive.util.EngineChainSqlGenerator engine_chain_demo_data.sql
```

## 结果说明

- 输出文件默认名：`engine_chain_demo_data.sql`
- SQL 内包含清理语句（`DELETE`），导入前请确认目标库环境
- 该脚本用于“批量造数初始化”，不负责回放事件流本身
