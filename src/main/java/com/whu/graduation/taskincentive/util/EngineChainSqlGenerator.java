package com.whu.graduation.taskincentive.util;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;

/**
 * 以 TaskEngine 为入口链路设计的造数脚本：
 * - 生成用户、任务、库存、用户任务实例、风控规则/配额/黑白名单
 * - 用户与任务创建时间默认分布在最近 7 天
 */
public class EngineChainSqlGenerator {

    private static final DateTimeFormatter DT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final String DEFAULT_BCRYPT = "$2a$10$7EqJtq98hPqEX7fNZaFWoOQ3z6M4s5xj8gJHjQ2j1bVjAqLr7Qw4K";

    private static final int DAYS = 7;
    private static final int USER_MIN_PER_DAY = 80;
    private static final int USER_MAX_PER_DAY = 150;
    private static final int TASK_MIN_PER_DAY = 4;
    private static final int TASK_MAX_PER_DAY = 8;

    private static final Random R = new Random(20260322L);

    private long idSeq = 2000000000000000000L;

    private final List<UserSeed> users = new ArrayList<>();
    private final List<BadgeSeed> badges = new ArrayList<>();
    private final Map<Integer, Long> badgeIdByCode = new HashMap<>();

    private final List<TaskSeed> tasks = new ArrayList<>();
    private final List<TaskStockSeed> stocks = new ArrayList<>();
    private final List<UserTaskInstanceSeed> instances = new ArrayList<>();
    private final List<UserActionLogSeed> actionLogs = new ArrayList<>();
    private final List<UserRewardRecordSeed> rewardRecords = new ArrayList<>();
    private final Map<String, UserBadgeSeed> userBadgeUnique = new LinkedHashMap<>();
    private final Map<Long, Integer> userPointDelta = new HashMap<>();

    private final List<RiskListSeed> whitelists = new ArrayList<>();
    private final List<RiskListSeed> blacklists = new ArrayList<>();
    private final List<RiskQuotaSeed> quotas = new ArrayList<>();
    private final List<RiskRuleSeed> rules = new ArrayList<>();

    public static void main(String[] args) throws Exception {
        String output = args.length > 0 ? args[0] : "engine_chain_demo_data.sql";
        new EngineChainSqlGenerator().generate(output);
        System.out.println("SQL generated: " + output);
    }

    public void generate(String outputFile) throws IOException {
        LocalDate end = LocalDate.now();
        LocalDate start = end.minusDays(DAYS - 1L);

        buildUsers(start, end);
        buildBadges(start);
        buildTasks(start, end);
        buildUserTaskInstances(end);
        buildActionAndRewardData();
        buildRiskLists();
        buildRiskQuotas();
        buildRiskRules(start, end);

        writeSql(outputFile);
    }

    private void buildUsers(LocalDate start, LocalDate end) {
        String[] family = {"王", "李", "张", "刘", "陈", "杨", "赵", "黄", "吴", "周", "徐", "孙", "马", "朱", "胡", "郭"};
        String[] given = {"子涵", "雨桐", "宇轩", "梓豪", "欣怡", "晨曦", "嘉宁", "思远", "若彤", "浩然", "婉清", "俊杰", "诗涵", "天宇", "依诺", "博文"};

        LocalDate d = start;
        while (!d.isAfter(end)) {
            int n = rand(USER_MIN_PER_DAY, USER_MAX_PER_DAY);
            for (int i = 0; i < n; i++) {
                long id = nextId();
                String username = family[R.nextInt(family.length)]
                        + given[R.nextInt(given.length)] + "_" + d.format(DateTimeFormatter.BASIC_ISO_DATE) + "_" + (i + 1);
                LocalDateTime created = atRandomTime(d, 7, 23);
                users.add(new UserSeed(id, username, DEFAULT_BCRYPT, "ROLE_USER", rand(0, 200), created, created));
            }
            d = d.plusDays(1);
        }

        // 管理员和测试用户
        LocalDateTime adminTime = LocalDateTime.of(end.minusDays(1), LocalTime.of(10, 0));
        users.add(new UserSeed(nextId(), "系统管理员", DEFAULT_BCRYPT, "ROLE_ADMIN", 9999, adminTime, adminTime));
        users.add(new UserSeed(nextId(), "风控测试员", DEFAULT_BCRYPT, "ROLE_USER", 1000, adminTime, adminTime));
    }

    private void buildBadges(LocalDate firstDay) {
        String[][] badgeData = {
                {"签到新秀", "完成首次签到，迈出成长第一步", "https://img.icons8.com/color/96/calendar--v1.png"},
                {"签到达人", "连续签到达到3天，保持稳定节奏", "https://img.icons8.com/color/96/today.png"},
                {"全勤标兵", "连续签到达到7天，自律可嘉", "https://img.icons8.com/color/96/checked-calendar.png"},
                {"晨光学习者", "在上午时段保持高频学习", "https://img.icons8.com/color/96/sun--v1.png"},
                {"夜航学习者", "在夜间时段依然坚持学习", "https://img.icons8.com/color/96/moon-symbol.png"},
                {"学习新星", "累计学习时长达到阶段目标", "https://img.icons8.com/color/96/star--v1.png"},
                {"知识探索者", "完成多样化学习任务挑战", "https://img.icons8.com/color/96/compass--v1.png"},
                {"效率先锋", "单位时间学习效率表现优异", "https://img.icons8.com/color/96/lightning-bolt--v1.png"},
                {"任务征服者", "完成多个任务并保持高完成率", "https://img.icons8.com/color/96/trophy--v1.png"},
                {"冲刺王者", "在短时间内达成高强度目标", "https://img.icons8.com/color/96/rocket--v1.png"},
                {"进阶学徒", "完成进阶层级任务第一档", "https://img.icons8.com/color/96/medal2--v1.png"},
                {"进阶骑士", "完成进阶层级任务第二档", "https://img.icons8.com/color/96/medal-first-place--v1.png"},
                {"进阶宗师", "完成进阶层级任务最高档", "https://img.icons8.com/color/96/prize.png"},
                {"目标达成者", "单日目标达成率持续稳定", "https://img.icons8.com/color/96/goal--v1.png"},
                {"连续挑战者", "连续挑战多个任务周期", "https://img.icons8.com/color/96/recurring-appointment.png"},
                {"坚持不懈", "在低活跃期依然保持打卡", "https://img.icons8.com/color/96/strength.png"},
                {"行动派", "接取任务后快速进入执行状态", "https://img.icons8.com/color/96/running.png"},
                {"稳步前行", "任务进度长期保持正向增长", "https://img.icons8.com/color/96/upward-arrow.png"},
                {"高效执行", "任务执行速度显著高于平均", "https://img.icons8.com/color/96/speed.png"},
                {"专注达人", "专注学习并减少无效操作", "https://img.icons8.com/color/96/focus.png"},
                {"思维火花", "在关键节点完成高质量任务", "https://img.icons8.com/color/96/idea--v1.png"},
                {"协作之星", "参与协作类活动并表现积极", "https://img.icons8.com/color/96/teamwork.png"},
                {"成长飞轮", "持续完成任务形成正反馈", "https://img.icons8.com/color/96/recycle-sign.png"},
                {"卓越表现", "多项指标同时达到优秀阈值", "https://img.icons8.com/color/96/crown.png"},
                {"周度活跃", "连续一周保持任务活跃", "https://img.icons8.com/color/96/timeline-week.png"},
                {"月度活跃", "月度任务活跃度达到标准", "https://img.icons8.com/color/96/timeline-month.png"},
                {"风控安全", "行为稳定且风险评分优秀", "https://img.icons8.com/color/96/security-checked.png"},
                {"质量标杆", "任务完成质量长期保持高位", "https://img.icons8.com/color/96/quality.png"},
                {"荣耀时刻", "达成高难度综合目标", "https://img.icons8.com/color/96/fireworks.png"},
                {"巅峰成就", "达成系统设定的顶级荣誉", "https://img.icons8.com/color/96/laurel-wreath--v1.png"}
        };
        for (int i = 0; i < badgeData.length; i++) {
            long id = nextId();
            int code = 3001 + i;
            LocalDateTime ct = LocalDateTime.of(firstDay, LocalTime.of(9, 0)).plusMinutes(i);
            badges.add(new BadgeSeed(id, badgeData[i][0], code,
                    badgeData[i][2], badgeData[i][1], ct, ct));
            badgeIdByCode.put(code, id);
        }
    }

    private void buildTasks(LocalDate start, LocalDate end) {
        String[] taskTypes = {"ACCUMULATE", "CONTINUOUS", "STAIR", "WINDOW_ACCUMULATE"};
        String[] rewardTypes = {"POINT", "BADGE", "ITEM"};

        LocalDate d = start;
        int daySeq = 1;
        while (!d.isAfter(end)) {
            int n = rand(TASK_MIN_PER_DAY, TASK_MAX_PER_DAY);
            for (int i = 0; i < n; i++) {
                long id = nextId();
                String taskType = taskTypes[R.nextInt(taskTypes.length)];
                String triggerEvent = chooseTriggerEvent(taskType);
                String stockType = R.nextDouble() < 0.30 ? "LIMITED" : "UNLIMITED";

                String rewardType = rewardTypes[R.nextInt(rewardTypes.length)];
                int rewardValue = chooseRewardValue(rewardType);

                String ruleConfig = buildRuleConfig(taskType);
                Integer totalStock = "LIMITED".equals(stockType) ? rand(200, 1500) : null;

                LocalDateTime createTime = atRandomTime(d, 8, 22);
                LocalDateTime startTime = createTime.minusHours(rand(0, 12));
                LocalDateTime endTime = createTime.plusDays(rand(6, 20)).withHour(23).withMinute(59).withSecond(59);

                String name = genTaskName(taskType, triggerEvent, rewardType, rewardValue, daySeq, i + 1);
                TaskSeed task = new TaskSeed(id, name, taskType, stockType, triggerEvent, ruleConfig,
                        rewardType, rewardValue, totalStock, 1, startTime, endTime, createTime, createTime);
                tasks.add(task);

                if ("LIMITED".equals(stockType)) {
                    if ("STAIR".equals(taskType)) {
                        int stageCount = 3;
                        for (int s = 1; s <= stageCount; s++) {
                            stocks.add(new TaskStockSeed(task.id, s, totalStock, 0, createTime, createTime));
                        }
                    } else {
                        stocks.add(new TaskStockSeed(task.id, 1, totalStock, 0, createTime, createTime));
                    }
                }
            }
            d = d.plusDays(1);
            daySeq++;
        }
    }

    private void buildUserTaskInstances(LocalDate end) {
        Map<String, UserTaskInstanceSeed> uniq = new LinkedHashMap<>();

        for (UserSeed user : users) {
            int receiveCount = rand(2, 6);
            List<TaskSeed> visible = visibleTasksForUser(user);
            if (visible.isEmpty()) {
                continue;
            }

            for (int i = 0; i < receiveCount; i++) {
                TaskSeed task = visible.get(R.nextInt(visible.size()));
                String key = user.id + "_" + task.id;
                if (uniq.containsKey(key)) {
                    continue;
                }

                int status = chooseInstanceStatus();
                int progress = progressByStatus(task, status);
                String extra = buildExtraData(task, progress, status);

                LocalDateTime ct = between(user.createTime.toLocalDate(), end);
                LocalDateTime ut = ct.plusMinutes(rand(1, 240));
                uniq.put(key, new UserTaskInstanceSeed(nextId(), user.id, user.username, task.id, task.taskName,
                        progress, status, 0, extra, ct, ut));
            }
        }

        instances.addAll(uniq.values());
    }

    private void buildActionAndRewardData() {
        Map<Long, TaskSeed> taskById = new HashMap<>();
        for (TaskSeed task : tasks) {
            taskById.put(task.id, task);
        }

        for (UserTaskInstanceSeed ins : instances) {
            TaskSeed task = taskById.get(ins.taskId);
            if (task == null) {
                continue;
            }

            // Generate action logs for accepted and above statuses.
            if (ins.status >= 1 && ins.progress > 0) {
                actionLogs.add(new UserActionLogSeed(nextId(), ins.userId, task.triggerEvent,
                        ins.progress, ins.createTime));
            }

            // Preload reward records for completed instances.
            if (ins.status == 3) {
                int rewardStatus = "ITEM".equals(task.rewardType) ? (R.nextDouble() < 0.7 ? 1 : 0) : 1;
                rewardRecords.add(new UserRewardRecordSeed(nextId(), ins.userId, ins.taskId,
                        task.rewardType, rewardStatus, task.rewardValue, ins.updateTime));

                if ("POINT".equals(task.rewardType)) {
                    userPointDelta.merge(ins.userId, task.rewardValue, Integer::sum);
                } else if ("BADGE".equals(task.rewardType)) {
                    Long badgeId = badgeIdByCode.get(task.rewardValue);
                    if (badgeId != null) {
                        String key = ins.userId + "_" + badgeId;
                        userBadgeUnique.putIfAbsent(key,
                                new UserBadgeSeed(nextId(), ins.userId, badgeId, ins.updateTime));
                    }
                }
            }
        }

        // Backfill point balance to keep consistency with reward records.
        for (UserSeed user : users) {
            int delta = userPointDelta.getOrDefault(user.id, 0);
            user.pointBalance = Math.max(0, user.pointBalance + delta);
            user.updateTime = LocalDateTime.now().withNano(0);
        }
    }

    private void buildRiskLists() {
        Set<String> userCandidates = new TreeSet<>();
        Set<String> deviceCandidates = new TreeSet<>();
        Set<String> ipCandidates = new TreeSet<>();

        for (int i = 0; i < Math.min(users.size(), 30); i++) {
            userCandidates.add(String.valueOf(users.get(i).id));
            deviceCandidates.add("device-" + (1000 + i));
            ipCandidates.add("10.0." + (i / 10) + "." + (10 + i % 10));
        }

        int idx = 0;
        for (String u : userCandidates) {
            if (idx >= 3) break;
            whitelists.add(new RiskListSeed(nextId(), "USER", u, "系统造数", null, 1, LocalDateTime.now().minusDays(1)));
            idx++;
        }

        idx = 0;
        for (String u : userCandidates) {
            if (idx < 3) {
                idx++;
                continue;
            }
            if (idx >= 6) break;
            blacklists.add(new RiskListSeed(nextId(), "USER", u, "系统造数", null, 1, LocalDateTime.now().minusDays(1)));
            idx++;
        }

        idx = 0;
        for (String d : deviceCandidates) {
            if (idx >= 2) break;
            whitelists.add(new RiskListSeed(nextId(), "DEVICE", d, "系统造数", null, 1, LocalDateTime.now().minusDays(1)));
            idx++;
        }

        idx = 0;
        for (String d : deviceCandidates) {
            if (idx < 2) {
                idx++;
                continue;
            }
            if (idx >= 4) break;
            blacklists.add(new RiskListSeed(nextId(), "DEVICE", d, "系统造数", null, 1, LocalDateTime.now().minusDays(1)));
            idx++;
        }

        idx = 0;
        for (String ip : ipCandidates) {
            if (idx >= 2) break;
            whitelists.add(new RiskListSeed(nextId(), "IP", ip, "系统造数", null, 1, LocalDateTime.now().minusDays(1)));
            idx++;
        }

        idx = 0;
        for (String ip : ipCandidates) {
            if (idx < 2) {
                idx++;
                continue;
            }
            if (idx >= 4) break;
            blacklists.add(new RiskListSeed(nextId(), "IP", ip, "系统造数", null, 1, LocalDateTime.now().minusDays(1)));
            idx++;
        }
    }

    private void buildRiskQuotas() {
        LocalDateTime now = LocalDateTime.now();

        // GLOBAL
        quotas.add(new RiskQuotaSeed(nextId(), "全局分钟总量", "GLOBAL", "ALL", "MINUTE", 5000, 0, now.plusMinutes(1), now, "ALL", "ALL"));
        quotas.add(new RiskQuotaSeed(nextId(), "全局小时总量", "GLOBAL", "ALL", "HOUR", 20000, 0, now.plusHours(1), now, "ALL", "ALL"));
        quotas.add(new RiskQuotaSeed(nextId(), "全局日总量", "GLOBAL", "ALL", "DAY", 100000, 0, now.plusDays(1), now, "ALL", "ALL"));
        quotas.add(new RiskQuotaSeed(nextId(), "全局积分日总量", "GLOBAL", "ALL", "DAY", 60000, 0, now.plusDays(1), now, "POINT", "ALL"));

        // TASK ALL + specific tasks
        quotas.add(new RiskQuotaSeed(nextId(), "任务层级日总量", "TASK", "ALL", "DAY", 5000, 0, now.plusDays(1), now, "ALL", "ALL"));
        for (int i = 0; i < Math.min(6, tasks.size()); i++) {
            TaskSeed t = tasks.get(i);
            quotas.add(new RiskQuotaSeed(nextId(), "任务" + t.taskName + "日限额", "TASK", String.valueOf(t.id), "DAY", rand(200, 800), 0,
                    now.plusDays(1), now, t.rewardType, String.valueOf(t.id)));
        }

        // USER ALL + specific users
        quotas.add(new RiskQuotaSeed(nextId(), "用户层级小时总量", "USER", "ALL", "HOUR", 80, 0, now.plusHours(1), now, "ALL", "ALL"));
        quotas.add(new RiskQuotaSeed(nextId(), "用户层级日总量", "USER", "ALL", "DAY", 300, 0, now.plusDays(1), now, "ALL", "ALL"));
        for (int i = 0; i < Math.min(10, users.size()); i++) {
            quotas.add(new RiskQuotaSeed(nextId(), "用户" + users.get(i).username + "日限额", "USER", String.valueOf(users.get(i).id), "DAY", rand(50, 150), 0,
                    now.plusDays(1), now, "ALL", "ALL"));
        }
    }

    private void buildRiskRules(LocalDate start, LocalDate end) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime startTime = LocalDateTime.of(start, LocalTime.MIN);
        LocalDateTime endTime = LocalDateTime.of(end.plusDays(14), LocalTime.of(23, 59, 59));

        rules.add(new RiskRuleSeed(nextId(), "分钟频控拦截", "FREQUENCY", 100, 1,
                "#count_1m >= 20", "REJECT", null, startTime, endTime, 1, "系统造数", "系统造数", now, now));
        rules.add(new RiskRuleSeed(nextId(), "IP聚集拦截", "IP", 98, 1,
                "#ip_count_1m >= 60", "REJECT", null, startTime, endTime, 1, "系统造数", "系统造数", now, now));
        rules.add(new RiskRuleSeed(nextId(), "设备高频拦截", "DEVICE", 97, 1,
                "#device_count_1m >= 30", "REJECT", null, startTime, endTime, 1, "系统造数", "系统造数", now, now));
        rules.add(new RiskRuleSeed(nextId(), "日累计额度冻结", "AMOUNT", 90, 1,
                "#amount_1d >= 3000", "FREEZE", null, startTime, endTime, 1, "系统造数", "系统造数", now, now));
        rules.add(new RiskRuleSeed(nextId(), "小时异常复核", "FREQUENCY", 85, 1,
                "#count_1h >= 120", "REVIEW", null, startTime, endTime, 1, "系统造数", "系统造数", now, now));
        rules.add(new RiskRuleSeed(nextId(), "多设备切换复核", "DEVICE", 80, 1,
                "#distinct_device_1d >= 4", "REVIEW", null, startTime, endTime, 1, "系统造数", "系统造数", now, now));
        rules.add(new RiskRuleSeed(nextId(), "中风险降级", "DEGRADE", 70, 1,
                "#count_1m >= 10 and #count_1m < 20 and #amount_1d < 3000", "DEGRADE_PASS", "{\"ratio\":0.5}",
                startTime, endTime, 1, "系统造数", "系统造数", now, now));
        rules.add(new RiskRuleSeed(nextId(), "凌晨时段降级", "TIME", 65, 1,
                "#eventTime != null and #eventTime.hour >= 1 and #eventTime.hour < 6 and #count_1m >= 8",
                "DEGRADE_PASS", "{\"ratio\":0.3}", startTime, endTime, 1, "系统造数", "系统造数", now, now));
    }

    private void writeSql(String outputFile) throws IOException {
        try (PrintWriter out = new PrintWriter(new FileWriter(outputFile))) {
            out.println("-- engine-chain demo data generated at " + LocalDateTime.now().format(DT));
            out.println("SET NAMES utf8mb4;");
            out.println("SET FOREIGN_KEY_CHECKS = 0;");
            out.println();

            out.println("DELETE FROM reward_freeze_record;");
            out.println("DELETE FROM risk_decision_log;");
            out.println("DELETE FROM risk_rule;");
            out.println("DELETE FROM risk_quota;");
            out.println("DELETE FROM risk_blacklist;");
            out.println("DELETE FROM risk_whitelist;");
            out.println("DELETE FROM user_badge;");
            out.println("DELETE FROM user_reward_record;");
            out.println("DELETE FROM user_action_log;");
            out.println("DELETE FROM user_task_instance;");
            out.println("DELETE FROM task_stock;");
            out.println("DELETE FROM task_config;");
            out.println("DELETE FROM badge;");
            out.println("DELETE FROM `user`;");
            out.println();

            for (UserSeed u : users) {
                out.printf(Locale.ROOT,
                        "INSERT INTO `user` (`id`,`username`,`PASSWORD`,`roles`,`point_balance`,`create_time`,`update_time`) VALUES (%d,'%s','%s','%s',%d,'%s','%s');%n",
                        u.id, esc(u.username), esc(u.password), esc(u.roles), u.pointBalance,
                        u.createTime.format(DT), u.updateTime.format(DT));
            }
            out.println();

            for (BadgeSeed b : badges) {
                out.printf(Locale.ROOT,
                        "INSERT INTO badge (`id`,`name`,`code`,`image_url`,`description`,`create_time`,`update_time`) VALUES (%d,'%s',%d,'%s','%s','%s','%s');%n",
                        b.id, esc(b.name), b.code, esc(b.imageUrl), esc(b.description),
                        b.createTime.format(DT), b.updateTime.format(DT));
            }
            out.println();

            for (TaskSeed t : tasks) {
                out.printf(Locale.ROOT,
                        "INSERT INTO task_config (`id`,`task_name`,`task_type`,`stock_type`,`trigger_event`,`rule_config`,`reward_type`,`reward_value`,`total_stock`,`status`,`start_time`,`end_time`,`create_time`,`update_time`) " +
                                "VALUES (%d,'%s','%s','%s','%s','%s','%s',%d,%s,%d,'%s','%s','%s','%s');%n",
                        t.id, esc(t.taskName), t.taskType, t.stockType, t.triggerEvent, esc(t.ruleConfig),
                        t.rewardType, t.rewardValue,
                        t.totalStock == null ? "NULL" : String.valueOf(t.totalStock),
                        t.status,
                        t.startTime.format(DT), t.endTime.format(DT), t.createTime.format(DT), t.updateTime.format(DT));
            }
            out.println();

            for (TaskStockSeed s : stocks) {
                out.printf(Locale.ROOT,
                        "INSERT INTO task_stock (`task_id`,`stage_index`,`available_stock`,`version`,`create_time`,`update_time`) VALUES (%d,%d,%d,%d,'%s','%s');%n",
                        s.taskId, s.stageIndex, s.availableStock, s.version,
                        s.createTime.format(DT), s.updateTime.format(DT));
            }
            out.println();

            for (UserTaskInstanceSeed s : instances) {
                out.printf(Locale.ROOT,
                        "INSERT INTO user_task_instance (`id`,`user_id`,`user_name`,`task_id`,`task_name`,`progress`,`status`,`version`,`extra_data`,`create_time`,`update_time`) VALUES (%d,%d,'%s',%d,'%s',%d,%d,%d,%s,'%s','%s');%n",
                        s.id, s.userId, esc(s.userName), s.taskId, esc(s.taskName), s.progress, s.status, s.version,
                        s.extraData == null ? "NULL" : ("'" + esc(s.extraData) + "'"),
                        s.createTime.format(DT), s.updateTime.format(DT));
            }
            out.println();

            for (UserActionLogSeed l : actionLogs) {
                out.printf(Locale.ROOT,
                        "INSERT INTO user_action_log (`id`,`user_id`,`action_type`,`action_value`,`create_time`) VALUES (%d,%d,'%s',%d,'%s');%n",
                        l.id, l.userId, esc(l.actionType), l.actionValue, l.createTime.format(DT));
            }
            out.println();

            for (UserRewardRecordSeed r : rewardRecords) {
                out.printf(Locale.ROOT,
                        "INSERT INTO user_reward_record (`id`,`user_id`,`task_id`,`reward_type`,`status`,`reward_value`,`create_time`) VALUES (%d,%d,%d,'%s',%d,%d,'%s');%n",
                        r.id, r.userId, r.taskId, esc(r.rewardType), r.status, r.rewardValue, r.createTime.format(DT));
            }
            out.println();

            for (UserBadgeSeed b : userBadgeUnique.values()) {
                out.printf(Locale.ROOT,
                        "INSERT INTO user_badge (`id`,`user_id`,`badge_id`,`acquire_time`) VALUES (%d,%d,%d,'%s');%n",
                        b.id, b.userId, b.badgeId, b.acquireTime.format(DT));
            }
            out.println();

            for (RiskListSeed s : whitelists) {
                out.printf(Locale.ROOT,
                        "INSERT INTO risk_whitelist (`id`,`target_type`,`target_value`,`source`,`expire_at`,`status`,`created_at`) VALUES (%d,'%s','%s','%s',%s,%d,'%s');%n",
                        s.id, s.targetType, esc(s.targetValue), esc(s.source),
                        s.expireAt == null ? "NULL" : ("'" + s.expireAt.format(DT) + "'"),
                        s.status, s.createdAt.format(DT));
            }
            out.println();

            for (RiskListSeed s : blacklists) {
                out.printf(Locale.ROOT,
                        "INSERT INTO risk_blacklist (`id`,`target_type`,`target_value`,`source`,`expire_at`,`status`,`created_at`) VALUES (%d,'%s','%s','%s',%s,%d,'%s');%n",
                        s.id, s.targetType, esc(s.targetValue), esc(s.source),
                        s.expireAt == null ? "NULL" : ("'" + s.expireAt.format(DT) + "'"),
                        s.status, s.createdAt.format(DT));
            }
            out.println();

            for (RiskQuotaSeed q : quotas) {
                out.printf(Locale.ROOT,
                        "INSERT INTO risk_quota (`id`,`quota_name`,`scope_type`,`scope_id`,`period_type`,`limit_value`,`used_value`,`reset_at`,`created_at`,`resource_type`,`resource_id`) VALUES (%d,'%s','%s','%s','%s',%d,%d,'%s','%s','%s','%s');%n",
                        q.id, esc(q.quotaName), q.scopeType, esc(q.scopeId), q.periodType, q.limitValue, q.usedValue,
                        q.resetAt.format(DT), q.createdAt.format(DT), q.resourceType, esc(q.resourceId));
            }
            out.println();

            for (RiskRuleSeed r : rules) {
                out.printf(Locale.ROOT,
                        "INSERT INTO risk_rule (`id`,`name`,`type`,`priority`,`status`,`condition_expr`,`action`,`action_params`,`start_time`,`end_time`,`version`,`created_by`,`updated_by`,`created_at`,`updated_at`) VALUES (%d,'%s','%s',%d,%d,'%s','%s',%s,'%s','%s',%d,'%s','%s','%s','%s');%n",
                        r.id, esc(r.name), r.type, r.priority, r.status, esc(r.conditionExpr), r.action,
                        r.actionParams == null ? "NULL" : ("'" + esc(r.actionParams) + "'"),
                        r.startTime.format(DT), r.endTime.format(DT), r.version,
                        esc(r.createdBy), esc(r.updatedBy), r.createdAt.format(DT), r.updatedAt.format(DT));
            }

            out.println();
            for (UserSeed u : users) {
                out.printf(Locale.ROOT,
                        "UPDATE `user` SET `point_balance`=%d, `update_time`='%s' WHERE `id`=%d;%n",
                        u.pointBalance, u.updateTime.format(DT), u.id);
            }

            out.println();
            out.println("SET FOREIGN_KEY_CHECKS = 1;");
        }
    }

    private static String chooseTriggerEvent(String taskType) {
        if ("CONTINUOUS".equals(taskType)) {
            return "USER_SIGN";
        }
        if ("WINDOW_ACCUMULATE".equals(taskType)) {
            return "USER_LEARN";
        }
        return R.nextBoolean() ? "USER_LEARN" : "OTHER";
    }

    private int chooseRewardValue(String rewardType) {
        if ("POINT".equals(rewardType)) {
            return rand(5, 100);
        }
        if ("BADGE".equals(rewardType)) {
            List<Integer> codes = new ArrayList<>(badgeIdByCode.keySet());
            return codes.get(R.nextInt(codes.size()));
        }
        return rand(1, 5);
    }

    private String buildRuleConfig(String taskType) {
        if ("ACCUMULATE".equals(taskType)) {
            return "{\"targetValue\":" + rand(10, 80) + "}";
        }
        if ("CONTINUOUS".equals(taskType)) {
            return "{\"targetDays\":" + rand(3, 10) + "}";
        }
        if ("STAIR".equals(taskType)) {
            return "{\"stages\":[10,30,60],\"rewards\":[1,1,1]}";
        }
        return "{\"targetValue\":" + rand(40, 120) + ",\"windowMinutes\":" + rand(15, 60) + "}";
    }

    private String genTaskName(String taskType, String triggerEvent, String rewardType, int rewardValue, int daySeq, int index) {
        String typeName;
        if ("ACCUMULATE".equals(taskType)) {
            typeName = "累计";
        } else if ("CONTINUOUS".equals(taskType)) {
            typeName = "连续";
        } else if ("STAIR".equals(taskType)) {
            typeName = "阶梯";
        } else {
            typeName = "时间窗累计";
        }

        String eventName = "USER_SIGN".equals(triggerEvent) ? "签到" : ("USER_LEARN".equals(triggerEvent) ? "学习" : "通用行为");
        String rewardName = "POINT".equals(rewardType) ? (rewardValue + "积分")
                : ("BADGE".equals(rewardType) ? ("徽章编号" + rewardValue) : ("实物" + rewardValue + "件"));
        return String.format(Locale.ROOT, "%s%s任务-第%d天-%d号（奖励%s）", eventName, typeName, daySeq, index, rewardName);
    }

    private List<TaskSeed> visibleTasksForUser(UserSeed user) {
        List<TaskSeed> visible = new ArrayList<>();
        for (TaskSeed task : tasks) {
            if (!task.startTime.isAfter(user.updateTime.plusDays(1))) {
                visible.add(task);
            }
        }
        return visible;
    }

    private int chooseInstanceStatus() {
        double p = R.nextDouble();
        if (p < 0.65) return 1; // ACCEPTED
        if (p < 0.90) return 2; // IN_PROGRESS
        if (p < 0.97) return 3; // COMPLETED
        return 4;               // CANCELLED
    }

    private int progressByStatus(TaskSeed task, int status) {
        int target = parseTaskTarget(task);
        if (status == 4) {
            return Math.max(0, target / 3);
        }
        if (status == 3) {
            return Math.max(target, target + rand(0, target));
        }
        if (status == 2) {
            return Math.max(1, target - rand(1, Math.max(1, target / 2)));
        }
        return rand(0, Math.max(1, target / 3));
    }

    private int parseTaskTarget(TaskSeed task) {
        if ("ACCUMULATE".equals(task.taskType)) {
            return extractInt(task.ruleConfig, "targetValue", 10);
        }
        if ("CONTINUOUS".equals(task.taskType)) {
            return extractInt(task.ruleConfig, "targetDays", 3);
        }
        if ("STAIR".equals(task.taskType)) {
            return 60;
        }
        return extractInt(task.ruleConfig, "targetValue", 60);
    }

    private String buildExtraData(TaskSeed task, int progress, int status) {
        if ("CONTINUOUS".equals(task.taskType)) {
            int continuousDays = Math.max(1, Math.min(progress, 14));
            LocalDate last = LocalDate.now().minusDays(rand(0, 2));
            return "{\"lastSignDate\":\"" + last + "\",\"continuousDays\":" + continuousDays + "}";
        }
        if ("STAIR".equals(task.taskType)) {
            if (status == 3) {
                return "{\"grantedStages\":[1,2,3]}";
            }
            if (progress >= 30) {
                return "{\"grantedStages\":[1,2]}";
            }
            if (progress >= 10) {
                return "{\"grantedStages\":[1]}";
            }
            return "{\"grantedStages\":[]}";
        }
        if ("WINDOW_ACCUMULATE".equals(task.taskType)) {
            LocalDateTime t1 = LocalDateTime.now().minusMinutes(rand(5, 20));
            LocalDateTime t2 = LocalDateTime.now().minusMinutes(rand(1, 4));
            int v1 = Math.max(1, progress / 2);
            int v2 = Math.max(1, progress - v1);
            return "{\"entries\":[{\"time\":\"" + t1 + "\",\"value\":" + v1 + "},{\"time\":\"" + t2 + "\",\"value\":" + v2 + "}]}";
        }
        return null;
    }

    private static int extractInt(String json, String key, int defaultValue) {
        String pattern = "\"" + key + "\":";
        int i = json.indexOf(pattern);
        if (i < 0) return defaultValue;
        int start = i + pattern.length();
        int end = start;
        while (end < json.length() && Character.isDigit(json.charAt(end))) {
            end++;
        }
        if (end == start) return defaultValue;
        try {
            return Integer.parseInt(json.substring(start, end));
        } catch (Exception ignored) {
            return defaultValue;
        }
    }

    private long nextId() {
        return idSeq++;
    }

    private static int rand(int min, int max) {
        return min + R.nextInt(max - min + 1);
    }

    private static LocalDateTime atRandomTime(LocalDate date, int startHour, int endHour) {
        return LocalDateTime.of(date, LocalTime.of(rand(startHour, endHour), rand(0, 59), rand(0, 59)));
    }

    private static LocalDateTime between(LocalDate start, LocalDate end) {
        long days = Math.max(0, end.toEpochDay() - start.toEpochDay());
        LocalDate d = start.plusDays(days == 0 ? 0 : rand(0, (int) days));
        return atRandomTime(d, 8, 23);
    }

    private static String esc(String s) {
        return s == null ? "" : s.replace("\\", "\\\\").replace("'", "''");
    }

    static class UserSeed {
        long id;
        String username;
        String password;
        String roles;
        int pointBalance;
        LocalDateTime createTime;
        LocalDateTime updateTime;

        UserSeed(long id, String username, String password, String roles, int pointBalance,
                 LocalDateTime createTime, LocalDateTime updateTime) {
            this.id = id;
            this.username = username;
            this.password = password;
            this.roles = roles;
            this.pointBalance = pointBalance;
            this.createTime = createTime;
            this.updateTime = updateTime;
        }
    }

    static class BadgeSeed {
        long id;
        String name;
        int code;
        String imageUrl;
        String description;
        LocalDateTime createTime;
        LocalDateTime updateTime;

        BadgeSeed(long id, String name, int code, String imageUrl, String description,
                  LocalDateTime createTime, LocalDateTime updateTime) {
            this.id = id;
            this.name = name;
            this.code = code;
            this.imageUrl = imageUrl;
            this.description = description;
            this.createTime = createTime;
            this.updateTime = updateTime;
        }
    }

    static class TaskSeed {
        long id;
        String taskName;
        String taskType;
        String stockType;
        String triggerEvent;
        String ruleConfig;
        String rewardType;
        int rewardValue;
        Integer totalStock;
        int status;
        LocalDateTime startTime;
        LocalDateTime endTime;
        LocalDateTime createTime;
        LocalDateTime updateTime;

        TaskSeed(long id, String taskName, String taskType, String stockType, String triggerEvent,
                 String ruleConfig, String rewardType, int rewardValue, Integer totalStock, int status,
                 LocalDateTime startTime, LocalDateTime endTime, LocalDateTime createTime, LocalDateTime updateTime) {
            this.id = id;
            this.taskName = taskName;
            this.taskType = taskType;
            this.stockType = stockType;
            this.triggerEvent = triggerEvent;
            this.ruleConfig = ruleConfig;
            this.rewardType = rewardType;
            this.rewardValue = rewardValue;
            this.totalStock = totalStock;
            this.status = status;
            this.startTime = startTime;
            this.endTime = endTime;
            this.createTime = createTime;
            this.updateTime = updateTime;
        }
    }

    static class TaskStockSeed {
        long taskId;
        int stageIndex;
        int availableStock;
        int version;
        LocalDateTime createTime;
        LocalDateTime updateTime;

        TaskStockSeed(long taskId, int stageIndex, int availableStock, int version,
                      LocalDateTime createTime, LocalDateTime updateTime) {
            this.taskId = taskId;
            this.stageIndex = stageIndex;
            this.availableStock = availableStock;
            this.version = version;
            this.createTime = createTime;
            this.updateTime = updateTime;
        }
    }

    static class UserTaskInstanceSeed {
        long id;
        long userId;
        String userName;
        long taskId;
        String taskName;
        int progress;
        int status;
        int version;
        String extraData;
        LocalDateTime createTime;
        LocalDateTime updateTime;

        UserTaskInstanceSeed(long id, long userId, String userName, long taskId, String taskName,
                             int progress, int status, int version,
                             String extraData, LocalDateTime createTime, LocalDateTime updateTime) {
            this.id = id;
            this.userId = userId;
            this.userName = userName;
            this.taskId = taskId;
            this.taskName = taskName;
            this.progress = progress;
            this.status = status;
            this.version = version;
            this.extraData = extraData;
            this.createTime = createTime;
            this.updateTime = updateTime;
        }
    }

    static class UserActionLogSeed {
        long id;
        long userId;
        String actionType;
        int actionValue;
        LocalDateTime createTime;

        UserActionLogSeed(long id, long userId, String actionType, int actionValue, LocalDateTime createTime) {
            this.id = id;
            this.userId = userId;
            this.actionType = actionType;
            this.actionValue = actionValue;
            this.createTime = createTime;
        }
    }

    static class UserRewardRecordSeed {
        long id;
        long userId;
        long taskId;
        String rewardType;
        int status;
        int rewardValue;
        LocalDateTime createTime;

        UserRewardRecordSeed(long id, long userId, long taskId, String rewardType,
                             int status, int rewardValue, LocalDateTime createTime) {
            this.id = id;
            this.userId = userId;
            this.taskId = taskId;
            this.rewardType = rewardType;
            this.status = status;
            this.rewardValue = rewardValue;
            this.createTime = createTime;
        }
    }

    static class UserBadgeSeed {
        long id;
        long userId;
        long badgeId;
        LocalDateTime acquireTime;

        UserBadgeSeed(long id, long userId, long badgeId, LocalDateTime acquireTime) {
            this.id = id;
            this.userId = userId;
            this.badgeId = badgeId;
            this.acquireTime = acquireTime;
        }
    }

    static class RiskListSeed {
        long id;
        String targetType;
        String targetValue;
        String source;
        LocalDateTime expireAt;
        int status;
        LocalDateTime createdAt;

        RiskListSeed(long id, String targetType, String targetValue, String source,
                     LocalDateTime expireAt, int status, LocalDateTime createdAt) {
            this.id = id;
            this.targetType = targetType;
            this.targetValue = targetValue;
            this.source = source;
            this.expireAt = expireAt;
            this.status = status;
            this.createdAt = createdAt;
        }
    }

    static class RiskQuotaSeed {
        long id;
        String quotaName;
        String scopeType;
        String scopeId;
        String periodType;
        int limitValue;
        int usedValue;
        LocalDateTime resetAt;
        LocalDateTime createdAt;
        String resourceType;
        String resourceId;

        RiskQuotaSeed(long id, String quotaName, String scopeType, String scopeId, String periodType,
                      int limitValue, int usedValue, LocalDateTime resetAt, LocalDateTime createdAt,
                      String resourceType, String resourceId) {
            this.id = id;
            this.quotaName = quotaName;
            this.scopeType = scopeType;
            this.scopeId = scopeId;
            this.periodType = periodType;
            this.limitValue = limitValue;
            this.usedValue = usedValue;
            this.resetAt = resetAt;
            this.createdAt = createdAt;
            this.resourceType = resourceType;
            this.resourceId = resourceId;
        }
    }

    static class RiskRuleSeed {
        long id;
        String name;
        String type;
        int priority;
        int status;
        String conditionExpr;
        String action;
        String actionParams;
        LocalDateTime startTime;
        LocalDateTime endTime;
        int version;
        String createdBy;
        String updatedBy;
        LocalDateTime createdAt;
        LocalDateTime updatedAt;

        RiskRuleSeed(long id, String name, String type, int priority, int status, String conditionExpr,
                     String action, String actionParams, LocalDateTime startTime, LocalDateTime endTime,
                     int version, String createdBy, String updatedBy, LocalDateTime createdAt, LocalDateTime updatedAt) {
            this.id = id;
            this.name = name;
            this.type = type;
            this.priority = priority;
            this.status = status;
            this.conditionExpr = conditionExpr;
            this.action = action;
            this.actionParams = actionParams;
            this.startTime = startTime;
            this.endTime = endTime;
            this.version = version;
            this.createdBy = createdBy;
            this.updatedBy = updatedBy;
            this.createdAt = createdAt;
            this.updatedAt = updatedAt;
        }
    }
}


