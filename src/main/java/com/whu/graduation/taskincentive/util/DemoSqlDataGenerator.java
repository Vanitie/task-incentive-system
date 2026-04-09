package com.whu.graduation.taskincentive.util;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Stream;

/**
 * 演示数据 SQL 生成器（按业务链路生成）
 * 1) user（两周每天100-300）
 * 2) badge（首日一次性约30）
 * 3) task_config（两周每天5-10，限量任务同步task_stock）
 * 4) user_task_instance（按唯一(user_id,task_id)插入，整体完成率~70%）
 * 5) user_action_log（按任务trigger_event生成）
 * 6) user_badge（徽章奖励且完成后落库，去重）
 * 7) user_reward_record（完成任务后发奖）
 * 8) 汇总积分回写user.point_balance
 */
public class DemoSqlDataGenerator {

    private static final DateTimeFormatter DT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter D = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final String DEFAULT_BCRYPT = "$2a$10$7EqJtq98hPqEX7fNZaFWoOQ3z6M4s5xj8gJHjQ2j1bVjAqLr7Qw4K";
    private static final String MANAGED_SQL_PREFIX = "demo_data";
    private static final Random R = new Random(20260317L); // 固定种子，保证可复现

    // 可调参数
    private static final int DAYS = 14;
    private static final int USER_MIN_PER_DAY = 100;
    private static final int USER_MAX_PER_DAY = 300;
    private static final int TASK_MIN_PER_DAY = 5;
    private static final int TASK_MAX_PER_DAY = 10;
    private static final double OVERALL_COMPLETE_RATE = 0.70; // 目标完成率
    private static final double RECEIVE_RATIO_MIN = 0.50;      // 当日抽样接取比例
    private static final double RECEIVE_RATIO_MAX = 1.00;
    private static final double LIMITED_TASK_RATIO = 0.25;     // 限量任务占比
    private static final double BADGE_REWARD_RATIO = 0.20;     // 徽章奖励占比
    private static final double PHYSICAL_REWARD_RATIO = 0.10;  // 实物奖励占比

    private long idSeq = 1000000000000000000L;

    private final List<UserSeed> allUsers = new ArrayList<>();
    private final Map<LocalDate, List<UserSeed>> usersByDay = new LinkedHashMap<>();

    private final List<BadgeSeed> badges = new ArrayList<>();
    private final Map<Integer, BadgeSeed> badgeByCode = new HashMap<>();

    private final List<TaskSeed> allTasks = new ArrayList<>();
    private final Map<LocalDate, List<TaskSeed>> tasksByDay = new LinkedHashMap<>();

    // 唯一键 userId_taskId -> instance
    private final Map<String, UserTaskInstanceSeed> userTaskIndex = new LinkedHashMap<>();

    private final List<UserActionLogSeed> actionLogs = new ArrayList<>();
    private final List<UserRewardRecordSeed> rewardRecords = new ArrayList<>();
    private final Map<String, UserBadgeSeed> userBadgeUnique = new LinkedHashMap<>(); // userId_badgeId

    private final Map<Long, Integer> userPointDelta = new HashMap<>();

    public static void main(String[] args) throws Exception {
        String out = args.length > 0 ? args[0] : "demo_data.sql";
        new DemoSqlDataGenerator().generate(out);
        System.out.println("SQL生成完成: " + out);
    }

    public void generate(String outputFile) throws IOException {
        LocalDate end = LocalDate.now();
        LocalDate start = end.minusDays(DAYS - 1L);

        buildUsers(start, end);
        buildBadges(start);
        buildTasks(start, end);
        buildUserTaskInstancesAndRewardsAndLogs(start, end);
        buildUserPointBalance();

        cleanupManagedSqlArtifacts(outputFile);
        writeSql(outputFile);
    }

    private void cleanupManagedSqlArtifacts(String outputFile) {
        if (outputFile == null || outputFile.trim().isEmpty()) {
            return;
        }
        Path outputPath = Path.of(outputFile).toAbsolutePath().normalize();
        Path dir = outputPath.getParent();
        if (dir == null || !Files.isDirectory(dir)) {
            return;
        }

        List<Path> managed = new ArrayList<>();
        try (Stream<Path> stream = Files.list(dir)) {
            stream.filter(Files::isRegularFile)
                    .filter(path -> isManagedSqlArtifact(path, outputPath))
                    .forEach(managed::add);
        } catch (IOException ignored) {
            return;
        }
        if (managed.isEmpty()) {
            return;
        }

        List<Path> testFiles = new ArrayList<>();
        List<Path> actualFiles = new ArrayList<>();
        for (Path path : managed) {
            if (isTestSql(path)) {
                testFiles.add(path);
            } else {
                actualFiles.add(path);
            }
        }
        testFiles.sort((a, b) -> safeLastModifiedDesc(a, b));
        actualFiles.sort((a, b) -> safeLastModifiedDesc(a, b));

        Set<Path> keep = new HashSet<>();
        keep.add(outputPath);
        if (isTestSql(outputPath)) {
            Path newestActual = firstExisting(actualFiles, outputPath);
            if (newestActual != null) {
                keep.add(newestActual);
            }
        } else {
            Path newestTest = firstExisting(testFiles, outputPath);
            if (newestTest != null) {
                keep.add(newestTest);
            }
        }

        for (Path path : managed) {
            if (!keep.contains(path.toAbsolutePath().normalize())) {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    // Best-effort cleanup.
                }
            }
        }
    }

    private static Path firstExisting(List<Path> candidates, Path outputPath) {
        for (Path candidate : candidates) {
            Path normalized = candidate.toAbsolutePath().normalize();
            if (!normalized.equals(outputPath.toAbsolutePath().normalize())) {
                return normalized;
            }
        }
        return null;
    }

    private static int safeLastModifiedDesc(Path a, Path b) {
        FileTime aTime = safeLastModified(a);
        FileTime bTime = safeLastModified(b);
        return bTime.compareTo(aTime);
    }

    private static FileTime safeLastModified(Path path) {
        try {
            return Files.getLastModifiedTime(path);
        } catch (IOException ignored) {
            return FileTime.fromMillis(0L);
        }
    }

    private static boolean isManagedSqlArtifact(Path candidate, Path outputPath) {
        String name = candidate.getFileName().toString().toLowerCase(Locale.ROOT);
        if (!name.endsWith(".sql")) {
            return false;
        }
        Path normalized = candidate.toAbsolutePath().normalize();
        if (normalized.equals(outputPath)) {
            return true;
        }
        return name.startsWith(MANAGED_SQL_PREFIX) || name.startsWith("demo-data");
    }

    private static boolean isTestSql(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.contains("test");
    }

    // 1) user
    private void buildUsers(LocalDate start, LocalDate end) {
        String[] family = {"王","李","张","刘","陈","杨","赵","黄","周","吴","徐","孙","胡","朱","高","林","何","郭","马","罗","梁","宋","郑","谢","韩","唐","冯","于"};
        String[] givenA = {"子","一","雨","安","思","佳","晨","欣","梓","可","若","梦","清","语","天","文","书","晓","嘉","宇","明","浩","俊","志","博","泽"};
        String[] givenB = {"轩","涵","妍","彤","宁","悦","晨","瑶","琳","菲","然","琪","阳","霖","峰","涛","恒","航","杰","楠","怡","萱","珂","辰","媛","希"};

        LocalDate d = start;
        while (!d.isAfter(end)) {
            int n = rand(USER_MIN_PER_DAY, USER_MAX_PER_DAY);
            List<UserSeed> dayUsers = new ArrayList<>(n);
            for (int i = 0; i < n; i++) {
                long id = nextId();
                String name = family[R.nextInt(family.length)]
                        + givenA[R.nextInt(givenA.length)]
                        + givenB[R.nextInt(givenB.length)];
                String username = name + "_" + d.format(D) + "_" + (i + 1);

                LocalDateTime ct = atRandomTime(d);
                UserSeed u = new UserSeed(id, username, DEFAULT_BCRYPT, "ROLE_USER", 0, ct, ct);
                dayUsers.add(u);
                allUsers.add(u);
            }
            usersByDay.put(d, dayUsers);
            d = d.plusDays(1);
        }
    }

    // 2) badge
    private void buildBadges(LocalDate firstDay) {
        String[] names = {
                "签到新星","签到达人","学习之星","坚持不懈","效率先锋","冲刺王者","知识探索者","任务征服者","周度活跃",
                "月度活跃","早起达人","夜学专家","分享达人","答题高手","闯关能手","目标达成","连续打卡7天","连续打卡14天",
                "任务完成50次","任务完成100次","成长之路","荣耀时刻","跃迁达人","效率之王","自律之星","行动派",
                "学习马拉松","步步为营","稳扎稳打","全勤标兵"
        };

        for (int i = 0; i < names.length; i++) {
            long id = nextId();
            int code = 1001 + i;
            LocalDateTime t = LocalDateTime.of(firstDay, LocalTime.of(9, 0)).plusMinutes(i);
            BadgeSeed b = new BadgeSeed(
                    id, names[i], code,
                    "https://cdn.example.com/badges/" + code + ".png",
                    "演示徽章-" + names[i], t, t
            );
            badges.add(b);
            badgeByCode.put(code, b);
        }
    }

    // 3) task_config + task_stock
    private void buildTasks(LocalDate start, LocalDate end) {
        String[] events = {"USER_SIGN", "USER_LEARN", "OTHER"};
        String[] taskTypePool = {"TASK_TYPE_BEHAVIOR", "TASK_TYPE_STAIR", "TASK_TYPE_LIMITED"};

        LocalDate d = start;
        int dayIndex = 0;
        while (!d.isAfter(end)) {
            int n = rand(TASK_MIN_PER_DAY, TASK_MAX_PER_DAY);
            List<TaskSeed> dayTasks = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                long id = nextId();
                String taskType = chooseTaskType(taskTypePool);
                String event = events[R.nextInt(events.length)];

                String rewardType = chooseRewardType();
                int rewardValue;
                if ("REWARD_BADGE".equals(rewardType)) {
                    List<Integer> codes = new ArrayList<>(badgeByCode.keySet());
                    rewardValue = codes.get(R.nextInt(codes.size())); // 对应badge.code
                } else if ("REWARD_POINT".equals(rewardType)) {
                    rewardValue = rand(5, 80);
                } else {
                    rewardValue = rand(1, 3);
                }

                int target = "USER_SIGN".equals(event) ? rand(1, 7) : ("USER_LEARN".equals(event) ? rand(10, 120) : rand(1, 20));
                String ruleJson = "{\"target\":" + target + "}";

                Integer totalStock = null;
                Integer availableStock = null;
                if ("TASK_TYPE_LIMITED".equals(taskType)) {
                    totalStock = rand(300, 3000);
                    availableStock = totalStock;
                }

                LocalDateTime create = atRandomTime(d);
                LocalDateTime startTime = create.minusHours(rand(0, 12));
                LocalDateTime endTime = create.plusDays(rand(5, 20)).withHour(23).withMinute(59).withSecond(59);

                String taskName = genTaskName(event, target, rewardType, rewardValue, dayIndex, i);

                TaskSeed t = new TaskSeed(
                        id, taskName, taskType, event, ruleJson, rewardType, rewardValue,
                        totalStock, availableStock, 1,
                        startTime, endTime, create, create
                );
                dayTasks.add(t);
                allTasks.add(t);
            }
            tasksByDay.put(d, dayTasks);
            d = d.plusDays(1);
            dayIndex++;
        }
    }

    // 4/5/6/7
    private void buildUserTaskInstancesAndRewardsAndLogs(LocalDate start, LocalDate end) {
        LocalDate d = start;
        while (!d.isAfter(end)) {
            List<UserSeed> existingUsers = new ArrayList<>();
            LocalDateTime dayEnd = LocalDateTime.of(d, LocalTime.of(23, 59, 59));
            for (UserSeed u : allUsers) {
                if (!u.createTime.isAfter(dayEnd)) existingUsers.add(u);
            }

            List<TaskSeed> visibleTasks = new ArrayList<>();
            LocalDateTime dayStart = LocalDateTime.of(d, LocalTime.MIN);
            for (TaskSeed t : allTasks) {
                if (!t.startTime.isAfter(dayEnd) && !t.endTime.isBefore(dayStart)) {
                    visibleTasks.add(t);
                }
            }

            if (existingUsers.isEmpty() || visibleTasks.isEmpty()) {
                d = d.plusDays(1);
                continue;
            }

            double receiveRatio = RECEIVE_RATIO_MIN + R.nextDouble() * (RECEIVE_RATIO_MAX - RECEIVE_RATIO_MIN);
            int plannedReceives = (int) Math.round(existingUsers.size() * visibleTasks.size() * receiveRatio * 0.06);
            plannedReceives = Math.max(existingUsers.size() / 3, Math.min(plannedReceives, existingUsers.size() * 8));

            int tries = 0;
            int created = 0;
            while (created < plannedReceives && tries < plannedReceives * 20) {
                tries++;
                UserSeed u = existingUsers.get(R.nextInt(existingUsers.size()));
                TaskSeed t = visibleTasks.get(R.nextInt(visibleTasks.size()));

                String uk = u.id + "_" + t.id;
                if (userTaskIndex.containsKey(uk)) continue;

                if ("TASK_TYPE_LIMITED".equals(t.taskType)) {
                    if (t.availableStock == null || t.availableStock <= 0) continue;
                    t.availableStock -= 1;
                }

                boolean completed = R.nextDouble() < OVERALL_COMPLETE_RATE;
                int target = parseTargetFromRule(t.ruleConfig);
                int progress = completed ? target : Math.max(0, target - rand(1, Math.max(1, target)));

                LocalDateTime ct = atRandomTime(d);
                LocalDateTime ut = completed ? ct.plusMinutes(rand(1, 180)) : ct.plusMinutes(rand(1, 90));

                UserTaskInstanceSeed ins = new UserTaskInstanceSeed(
                        nextId(), u.id, t.id, progress, completed ? 1 : 0, 0, null, ct, ut
                );
                userTaskIndex.put(uk, ins);
                created++;

                int actionValue = completed ? target : Math.max(1, progress);
                actionLogs.add(new UserActionLogSeed(nextId(), u.id, t.triggerEvent, actionValue, ct));

                if (completed) {
                    int rewardStatus = "REWARD_PHYSICAL".equals(t.rewardType) ? (R.nextDouble() < 0.7 ? 1 : 0) : 1;
                    rewardRecords.add(new UserRewardRecordSeed(
                            nextId(), u.id, t.id, t.rewardType, rewardStatus, t.rewardValue, ut
                    ));

                    if ("REWARD_POINT".equals(t.rewardType)) {
                        userPointDelta.merge(u.id, t.rewardValue, Integer::sum);
                    } else if ("REWARD_BADGE".equals(t.rewardType)) {
                        BadgeSeed b = badgeByCode.get(t.rewardValue); // rewardValue=badge.code
                        if (b != null) {
                            String bk = u.id + "_" + b.id;
                            userBadgeUnique.putIfAbsent(bk, new UserBadgeSeed(nextId(), u.id, b.id, ut));
                        }
                    }
                }
            }

            d = d.plusDays(1);
        }
    }

    // 8
    private void buildUserPointBalance() {
        for (UserSeed u : allUsers) {
            int delta = userPointDelta.getOrDefault(u.id, 0);
            u.pointBalance = Math.max(0, delta);
            u.updateTime = LocalDateTime.now().withNano(0);
        }
    }

    private void writeSql(String outputFile) throws IOException {
        try (PrintWriter out = new PrintWriter(new FileWriter(outputFile))) {
            out.println("-- demo data generated at " + LocalDateTime.now().format(DT));
            out.println("SET NAMES utf8mb4;");
            out.println("SET FOREIGN_KEY_CHECKS = 0;");
            out.println();

            out.println("DELETE FROM user_badge;");
            out.println("DELETE FROM user_reward_record;");
            out.println("DELETE FROM user_action_log;");
            out.println("DELETE FROM user_task_instance;");
            out.println("DELETE FROM task_stock;");
            out.println("DELETE FROM task_config;");
            out.println("DELETE FROM badge;");
            out.println("DELETE FROM `user`;");
            out.println();

            for (UserSeed u : allUsers) {
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

            for (TaskSeed t : allTasks) {
                out.printf(Locale.ROOT,
                        "INSERT INTO task_config (`id`,`task_name`,`task_type`,`trigger_event`,`rule_config`,`reward_type`,`reward_value`,`total_stock`,`status`,`start_time`,`end_time`,`create_time`,`update_time`) " +
                                "VALUES (%d,'%s','%s','%s','%s','%s',%d,%s,%d,'%s','%s','%s','%s');%n",
                        t.id, esc(t.taskName), t.taskType, t.triggerEvent, esc(t.ruleConfig),
                        t.rewardType, t.rewardValue,
                        t.totalStock == null ? "NULL" : String.valueOf(t.totalStock),
                        t.status, t.startTime.format(DT), t.endTime.format(DT), t.createTime.format(DT), t.updateTime.format(DT));
            }
            out.println();

            for (TaskSeed t : allTasks) {
                if ("TASK_TYPE_LIMITED".equals(t.taskType) && t.totalStock != null) {
                    int av = Math.max(0, t.availableStock == null ? t.totalStock : t.availableStock);
                    out.printf(Locale.ROOT,
                            "INSERT INTO task_stock (`task_id`,`available_stock`,`version`,`create_time`,`update_time`) VALUES (%d,%d,0,'%s','%s');%n",
                            t.id, av, t.createTime.format(DT), t.updateTime.format(DT));
                }
            }
            out.println();

            for (UserTaskInstanceSeed s : userTaskIndex.values()) {
                out.printf(Locale.ROOT,
                        "INSERT INTO user_task_instance (`id`,`user_id`,`task_id`,`progress`,`status`,`version`,`extra_data`,`create_time`,`update_time`) VALUES (%d,%d,%d,%d,%d,%d,%s,'%s','%s');%n",
                        s.id, s.userId, s.taskId, s.progress, s.status, s.version,
                        s.extraData == null ? "NULL" : ("'" + esc(s.extraData) + "'"),
                        s.createTime.format(DT), s.updateTime.format(DT));
            }
            out.println();

            for (UserActionLogSeed l : actionLogs) {
                out.printf(Locale.ROOT,
                        "INSERT INTO user_action_log (`id`,`user_id`,`action_type`,`action_value`,`create_time`) VALUES (%d,%d,'%s',%d,'%s');%n",
                        l.id, l.userId, l.actionType, l.actionValue, l.createTime.format(DT));
            }
            out.println();

            for (UserRewardRecordSeed r : rewardRecords) {
                out.printf(Locale.ROOT,
                        "INSERT INTO user_reward_record (`id`,`user_id`,`task_id`,`reward_type`,`status`,`reward_value`,`create_time`) VALUES (%d,%d,%d,'%s',%d,%d,'%s');%n",
                        r.id, r.userId, r.taskId, r.rewardType, r.status, r.rewardValue, r.createTime.format(DT));
            }
            out.println();

            for (UserBadgeSeed ub : userBadgeUnique.values()) {
                out.printf(Locale.ROOT,
                        "INSERT INTO user_badge (`id`,`user_id`,`badge_id`,`acquire_time`) VALUES (%d,%d,%d,'%s');%n",
                        ub.id, ub.userId, ub.badgeId, ub.acquireTime.format(DT));
            }
            out.println();

            for (UserSeed u : allUsers) {
                out.printf(Locale.ROOT,
                        "UPDATE `user` SET `point_balance`=%d, `update_time`='%s' WHERE `id`=%d;%n",
                        u.pointBalance, u.updateTime.format(DT), u.id);
            }

            out.println("SET FOREIGN_KEY_CHECKS = 1;");
        }
    }

    private long nextId() { return idSeq++; }

    private static int rand(int min, int max) { return min + R.nextInt(max - min + 1); }

    private static String esc(String s) {
        return s == null ? "" : s.replace("\\", "\\\\").replace("'", "''");
    }

    private static LocalDateTime atRandomTime(LocalDate d) {
        return LocalDateTime.of(d, LocalTime.of(rand(7, 23), rand(0, 59), rand(0, 59)));
    }

    private static String chooseTaskType(String[] pool) {
        if (R.nextDouble() < LIMITED_TASK_RATIO) return "TASK_TYPE_LIMITED";
        return R.nextBoolean() ? pool[0] : pool[1];
    }

    private static String chooseRewardType() {
        double p = R.nextDouble();
        if (p < BADGE_REWARD_RATIO) return "REWARD_BADGE";
        if (p < BADGE_REWARD_RATIO + PHYSICAL_REWARD_RATIO) return "REWARD_PHYSICAL";
        return "REWARD_POINT";
    }

    private static int parseTargetFromRule(String ruleJson) {
        int i = ruleJson.indexOf(":");
        int j = ruleJson.indexOf("}");
        if (i > 0 && j > i) {
            try { return Integer.parseInt(ruleJson.substring(i + 1, j).trim()); } catch (Exception ignored) {}
        }
        return 1;
    }

    private static String genTaskName(String event, int target, String rewardType, int rewardValue, int dayIndex, int idx) {
        String ev = "USER_SIGN".equals(event) ? "签到" : ("USER_LEARN".equals(event) ? "学习" : "行为");
        String rw = "REWARD_POINT".equals(rewardType) ? (rewardValue + "积分")
                : ("REWARD_BADGE".equals(rewardType) ? "徽章#" + rewardValue : ("实物x" + rewardValue));
        return String.format("%s任务D%02d-%02d（目标%d，奖励%s）", ev, dayIndex + 1, idx + 1, target, rw);
    }

    static class UserSeed {
        long id; String username; String password; String roles; int pointBalance;
        LocalDateTime createTime; LocalDateTime updateTime;
        UserSeed(long id, String username, String password, String roles, int pointBalance, LocalDateTime createTime, LocalDateTime updateTime) {
            this.id = id; this.username = username; this.password = password; this.roles = roles;
            this.pointBalance = pointBalance; this.createTime = createTime; this.updateTime = updateTime;
        }
    }

    static class BadgeSeed {
        long id; String name; int code; String imageUrl; String description;
        LocalDateTime createTime; LocalDateTime updateTime;
        BadgeSeed(long id, String name, int code, String imageUrl, String description, LocalDateTime createTime, LocalDateTime updateTime) {
            this.id = id; this.name = name; this.code = code; this.imageUrl = imageUrl; this.description = description;
            this.createTime = createTime; this.updateTime = updateTime;
        }
    }

    static class TaskSeed {
        long id; String taskName; String taskType; String triggerEvent; String ruleConfig;
        String rewardType; int rewardValue; Integer totalStock; Integer availableStock; int status;
        LocalDateTime startTime; LocalDateTime endTime; LocalDateTime createTime; LocalDateTime updateTime;

        TaskSeed(long id, String taskName, String taskType, String triggerEvent, String ruleConfig, String rewardType,
                 int rewardValue, Integer totalStock, Integer availableStock, int status,
                 LocalDateTime startTime, LocalDateTime endTime, LocalDateTime createTime, LocalDateTime updateTime) {
            this.id = id; this.taskName = taskName; this.taskType = taskType; this.triggerEvent = triggerEvent;
            this.ruleConfig = ruleConfig; this.rewardType = rewardType; this.rewardValue = rewardValue;
            this.totalStock = totalStock; this.availableStock = availableStock; this.status = status;
            this.startTime = startTime; this.endTime = endTime; this.createTime = createTime; this.updateTime = updateTime;
        }
    }

    static class UserTaskInstanceSeed {
        long id; long userId; long taskId; int progress; int status; int version; String extraData;
        LocalDateTime createTime; LocalDateTime updateTime;
        UserTaskInstanceSeed(long id, long userId, long taskId, int progress, int status, int version, String extraData, LocalDateTime createTime, LocalDateTime updateTime) {
            this.id = id; this.userId = userId; this.taskId = taskId; this.progress = progress; this.status = status;
            this.version = version; this.extraData = extraData; this.createTime = createTime; this.updateTime = updateTime;
        }
    }

    static class UserActionLogSeed {
        long id; long userId; String actionType; int actionValue; LocalDateTime createTime;
        UserActionLogSeed(long id, long userId, String actionType, int actionValue, LocalDateTime createTime) {
            this.id = id; this.userId = userId; this.actionType = actionType; this.actionValue = actionValue; this.createTime = createTime;
        }
    }

    static class UserRewardRecordSeed {
        long id; long userId; long taskId; String rewardType; int status; int rewardValue; LocalDateTime createTime;
        UserRewardRecordSeed(long id, long userId, long taskId, String rewardType, int status, int rewardValue, LocalDateTime createTime) {
            this.id = id; this.userId = userId; this.taskId = taskId; this.rewardType = rewardType; this.status = status;
            this.rewardValue = rewardValue; this.createTime = createTime;
        }
    }

    static class UserBadgeSeed {
        long id; long userId; long badgeId; LocalDateTime acquireTime;
        UserBadgeSeed(long id, long userId, long badgeId, LocalDateTime acquireTime) {
            this.id = id; this.userId = userId; this.badgeId = badgeId; this.acquireTime = acquireTime;
        }
    }
}
