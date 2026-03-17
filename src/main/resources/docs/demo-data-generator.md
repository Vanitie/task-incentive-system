# 演示数据生成工具说明

## 文件位置

- 生成器：`src/main/java/com/whu/graduation/taskincentive/util/DemoSqlDataGenerator.java`

## 功能覆盖

工具按业务链路生成以下 SQL：

1. `user`：两周内每天 100-300 用户
2. `badge`：首日一次性约 30 徽章
3. `task_config`：两周内每天 5-10 任务
4. `task_stock`：限量任务同步库存
5. `user_task_instance`：用户领取任务（唯一约束 `user_id + task_id`）
6. `user_action_log`：按任务触发事件生成行为日志
7. `user_reward_record`：完成任务后发奖
8. `user_badge`：徽章奖励落用户徽章表（去重）
9. 回写 `user.point_balance`：累计积分奖励

## 关键一致性规则

- `user_task_instance` 不会重复插入同一 `user_id + task_id`
- `REWARD_BADGE` 时，`reward_value` 为 `badge.code`
- 限量任务先扣减 `task_stock.available_stock`，不超发
- 完成率目标约 `70%`（随机分布，非绝对值）
- 用户积分只累计积分奖励

## 如何运行

在项目根目录执行：

```powershell
mvn -q -DskipTests compile
java -cp target/classes com.whu.graduation.taskincentive.util.DemoSqlDataGenerator demo_data.sql
