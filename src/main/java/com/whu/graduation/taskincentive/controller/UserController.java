package com.whu.graduation.taskincentive.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.whu.graduation.taskincentive.dao.entity.User;
import com.whu.graduation.taskincentive.dao.entity.UserActionLog;
import com.whu.graduation.taskincentive.dao.entity.UserRewardRecord;
import com.whu.graduation.taskincentive.dao.entity.UserTaskInstance;
import com.whu.graduation.taskincentive.dao.mapper.UserActionLogMapper;
import com.whu.graduation.taskincentive.dao.mapper.UserMapper;
import com.whu.graduation.taskincentive.dao.mapper.UserTaskInstanceMapper;
import com.whu.graduation.taskincentive.dto.ApiResponse;
import com.whu.graduation.taskincentive.dto.ChartData;
import com.whu.graduation.taskincentive.dto.PageResult;
import com.whu.graduation.taskincentive.dto.UserAdminDTO;
import com.whu.graduation.taskincentive.service.UserRewardRecordService;
import com.whu.graduation.taskincentive.service.UserActionLogService;
import com.whu.graduation.taskincentive.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 用户控制器
 */
@RestController
@RequestMapping("/api/user")
public class UserController {

    @Autowired
    private UserService userService;

    @Autowired
    private UserRewardRecordService userRewardRecordService;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private UserTaskInstanceMapper userTaskInstanceMapper;

    @Autowired
    private UserActionLogMapper userActionLogMapper;

    @Autowired
    private UserActionLogService userActionLogService;

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<User> getById(@PathVariable Long id){
        return ApiResponse.success(maskSensitive(userService.getById(id)));
    }

    @GetMapping("/list")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<PageResult<User>> listAll(@RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int size){
        if (page < 1 || size < 1 || size > 100) {
            return ApiResponse.error(400, "invalid page or size");
        }
        Page<User> p = new Page<>(page, size);
        p = userService.selectPage(p);
        List<User> safeItems = new ArrayList<>();
        if (p.getRecords() != null) {
            for (User u : p.getRecords()) {
                safeItems.add(maskSensitive(u));
            }
        }
        PageResult<User> pr = PageResult.<User>builder().total(p.getTotal()).page((int)p.getCurrent()).size((int)p.getSize()).items(safeItems).build();
        return ApiResponse.success(pr);
    }

    @PostMapping("/create")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Boolean> create(@RequestBody User user){
        return ApiResponse.success(userService.save(user));
    }

    @PutMapping("/update")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public ApiResponse<Boolean> update(@RequestBody User user, Authentication authentication){
        if (user == null || user.getId() == null) {
            return ApiResponse.error(400, "id required");
        }

        boolean isAdmin = authentication != null && authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch("ROLE_ADMIN"::equals);

        if (!isAdmin) {
            if (authentication == null || authentication.getName() == null) {
                return ApiResponse.error(401, "unauthorized");
            }
            User loginUser = userService.selectByUsername(authentication.getName());
            if (loginUser == null || !user.getId().equals(loginUser.getId())) {
                return ApiResponse.error(403, "forbidden");
            }
            // 普通用户仅允许更新自身基础资料，避免角色/积分越权。
            User patch = new User();
            patch.setId(user.getId());
            patch.setUsername(user.getUsername());
            return ApiResponse.success(userService.update(patch));
        }

        // 管理员允许维护用户基础字段与运营字段。
        User patch = new User();
        patch.setId(user.getId());
        patch.setUsername(user.getUsername());
        patch.setRoles(user.getRoles());
        patch.setPointBalance(user.getPointBalance());
        return ApiResponse.success(userService.update(patch));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Boolean> delete(@PathVariable Long id){
        return ApiResponse.success(userService.deleteById(id));
    }

    @PostMapping("/points")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Boolean> updatePoints(@RequestParam Long userId, @RequestParam Integer points){
        if (userId == null || points == null) {
            return ApiResponse.error(400, "userId and points required");
        }
        boolean ok = userService.updateUserPoints(userId, points);
        if (ok) {
            // 运营侧积分变动纳入统一奖励日志，便于后续对账与重放。
            UserRewardRecord opLog = UserRewardRecord.builder()
                    .userId(userId)
                    .taskId(0L)
                    .rewardType("OP_POINT")
                    .status(0)
                    .rewardValue(points)
                    .grantStatus(2)
                    .createTime(new Date())
                    .build();
            userRewardRecordService.save(opLog);
        }
        return ApiResponse.success(ok);
    }

    // helper to compute percent string
    private String computePercent(List<Long> data){
        if (data == null || data.size() < 2) return "+0%";
        int n = data.size();
        long last = data.get(n-1);
        long prev = data.get(n-2);
        if (prev == 0) return last == 0 ? "+0%" : "+100%";
        double p = ((double)(last - prev) / (double)prev) * 100.0;
        String sign = p >= 0 ? "+" : "";
        return String.format("%s%.0f%%", sign, p);
    }

    /**
     * 查询用户总数（返回过去7天的每日新增用户数据及汇总）
     */
    @GetMapping("/count/total")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<ChartData> countTotalUsers(){
        List<Long> data = userService.getUserCountLast7Days();
        long value = userService.countAllUsers();
        String percent = computePercent(data);
        ChartData chart = ChartData.builder()
                .icon("user")
                .name("用户总数")
                .value(value)
                .data(data)
                .percent(percent)
                .bgColor("#effaff")
                .color("#41b6ff")
                .duration(2200)
                .build();
        return ApiResponse.success(chart);
    }

    /**
     * 统计过去7天内的活跃用户数（每天去重 user_id）
     */
    @GetMapping("/count/active7days")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<ChartData> countActive7Days(){
        List<Long> data = userService.getActiveUserCountLast7Days();
        long value = lastOrZero(data);
        String percent = computePercent(data);
        ChartData chart = ChartData.builder()
                .icon("active")
                .name("活跃用户数")
                .value(value)
                .data(data)
                .percent(percent)
                .bgColor("#fff5f4")
                .color("#e85f33")
                .duration(1600)
                .build();
        return ApiResponse.success(chart);
    }

    /**
     * 统计过去7天内每日接取任务的用户数
     */
    // 替换 countToday 方法
    @GetMapping("/count/today")
    @PreAuthorize("hasAnyRole('ADMIN')")
    public ApiResponse<ChartData> countToday(){
        List<Long> data = userService.getTaskReceiveUserCountLast7Days();
        long value = lastOrZero(data);
        String percent = computePercent(data);
        ChartData chart = ChartData.builder()
                .icon("task")
                .name("今日参与任务用户数")
                .value(value)
                .data(data)
                .percent(percent)
                .bgColor("#eff8f4")
                .color("#26ce83")
                .duration(1500)
                .build();
        return ApiResponse.success(chart);
    }

    private long lastOrZero(List<Long> data) {
        if (data == null || data.isEmpty()) {
            return 0L;
        }
        return data.get(data.size() - 1);
    }

    private User maskSensitive(User user) {
        if (user == null) {
            return null;
        }
        return User.builder()
                .id(user.getId())
                .username(user.getUsername())
                .roles(user.getRoles())
                .pointBalance(user.getPointBalance())
                .createTime(user.getCreateTime())
                .updateTime(user.getUpdateTime())
                .build();
    }

    /**
     * 用户分页查询（多条件）
     */
    @GetMapping("/admin/page")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<PageResult<UserAdminDTO.UserAdminItem>> adminPage(
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) Boolean enabled,
            @RequestParam(required = false) String registerStart,
            @RequestParam(required = false) String registerEnd,
            @RequestParam(required = false) String activeStart,
            @RequestParam(required = false) String activeEnd,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        QueryWrapper<User> wrapper = new QueryWrapper<>();
        if (userId != null) {
            wrapper.eq("id", userId);
        }
        if (username != null && !username.trim().isEmpty()) {
            wrapper.like("username", username.trim());
        }
        if (role != null && !role.trim().isEmpty()) {
            wrapper.like("roles", role.toUpperCase());
        }
        if (registerStart != null && !registerStart.trim().isEmpty()) {
            wrapper.ge("create_time", registerStart.trim());
        }
        if (registerEnd != null && !registerEnd.trim().isEmpty()) {
            wrapper.le("create_time", registerEnd.trim());
        }
        wrapper.orderByDesc("create_time");

        Page<User> p = userMapper.selectPage(new Page<>(page, size), wrapper);
        List<UserAdminDTO.UserAdminItem> items = p.getRecords().stream()
                .map(this::toAdminItem)
                .filter(i -> enabled == null || enabled.equals(i.getEnabled()))
                .filter(i -> inActiveRange(i.getLastActiveTime(), activeStart, activeEnd))
                .collect(Collectors.toList());

        PageResult<UserAdminDTO.UserAdminItem> pr = PageResult.<UserAdminDTO.UserAdminItem>builder()
                .total(p.getTotal())
                .page((int) p.getCurrent())
                .size((int) p.getSize())
                .items(items)
                .build();
        return ApiResponse.success(pr);
    }

    /**
     * 用户详情
     */
    @GetMapping("/admin/{id}/detail")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<UserAdminDTO.UserDetail> adminDetail(@PathVariable Long id) {
        User user = userService.getById(id);
        if (user == null) {
            return ApiResponse.error(404, "user not found");
        }
        UserAdminDTO.UserDetail detail = UserAdminDTO.UserDetail.builder()
                .basic(toAdminItem(user))
                .pointSummary(buildPointSummary(id, user.getPointBalance()))
                .behavior7d(buildBehaviorSummary(id, 7))
                .behavior30d(buildBehaviorSummary(id, 30))
                .build();
        return ApiResponse.success(detail);
    }

    /**
     * 用户启停
     */
    @PutMapping("/admin/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Boolean> updateUserStatus(@RequestParam Long userId,
                                                 @RequestParam Boolean enabled,
                                                 Authentication authentication) {
        User user = userService.getById(userId);
        if (user == null) {
            return ApiResponse.error(404, "user not found");
        }
        if (Boolean.TRUE.equals(enabled)) {
            user.setRoles("ROLE_USER");
        } else {
            user.setRoles("ROLE_DISABLED");
        }
        boolean ok = userService.update(user);
        if (ok) {
            writeAdminAudit(authentication, userId, "ADMIN_STATUS_CHANGE", "enabled=" + enabled);
        }
        return ApiResponse.success(ok);
    }

    /**
     * 用户角色调整
     */
    @PutMapping("/admin/role")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Boolean> updateUserRole(@RequestParam Long userId,
                                               @RequestParam String role,
                                               Authentication authentication) {
        if (role == null || role.trim().isEmpty()) {
            return ApiResponse.error(400, "role required");
        }
        String normalized = role.trim().toUpperCase();
        if (!"USER".equals(normalized) && !"ADMIN".equals(normalized)
                && !"ROLE_USER".equals(normalized) && !"ROLE_ADMIN".equals(normalized)) {
            return ApiResponse.error(400, "role must be USER or ADMIN");
        }
        User user = userService.getById(userId);
        if (user == null) {
            return ApiResponse.error(404, "user not found");
        }
        if (!normalized.startsWith("ROLE_")) {
            normalized = "ROLE_" + normalized;
        }
        user.setRoles(normalized);
        boolean ok = userService.update(user);
        if (ok) {
            writeAdminAudit(authentication, userId, "ADMIN_ROLE_CHANGE", "role=" + normalized);
        }
        return ApiResponse.success(ok);
    }

    /**
     * 用户积分流水摘要
     */
    @GetMapping("/admin/{id}/points-summary")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<UserAdminDTO.PointSummary> pointSummary(@PathVariable Long id) {
        User user = userService.getById(id);
        if (user == null) {
            return ApiResponse.error(404, "user not found");
        }
        return ApiResponse.success(buildPointSummary(id, user.getPointBalance()));
    }

    /**
     * 用户行为摘要（7天/30天）
     */
    @GetMapping("/admin/{id}/behavior-summary")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<UserAdminDTO.BehaviorSummary> behaviorSummary(@PathVariable Long id,
                                                                     @RequestParam(defaultValue = "7") Integer days) {
        User user = userService.getById(id);
        if (user == null) {
            return ApiResponse.error(404, "user not found");
        }
        int d = (days == null || days <= 0) ? 7 : Math.min(days, 365);
        return ApiResponse.success(buildBehaviorSummary(id, d));
    }

    /**
     * 管理操作审计日志
     */
    @GetMapping("/admin/audit-logs")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<UserAdminDTO.AuditPage> auditLogs(
            @RequestParam(required = false) Long operatorUserId,
            @RequestParam(required = false) Long targetUserId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        QueryWrapper<UserActionLog> wrapper = new QueryWrapper<UserActionLog>()
                .like("action_type", "ADMIN_")
                .orderByDesc("create_time");
        if (operatorUserId != null) {
            wrapper.eq("user_id", operatorUserId);
        }
        if (targetUserId != null) {
            wrapper.eq("action_value", targetUserId.intValue());
        }
        Page<UserActionLog> p = userActionLogMapper.selectPage(new Page<>(page, size), wrapper);
        List<UserAdminDTO.AuditLogItem> items = p.getRecords().stream()
                .map(log -> UserAdminDTO.AuditLogItem.builder()
                        .id(log.getId())
                        .operatorUserId(log.getUserId())
                        .targetUserId(log.getActionValue() == null ? null : log.getActionValue().longValue())
                        .actionType(extractAuditAction(log.getActionType()))
                        .detail(extractAuditDetail(log.getActionType()))
                        .operateTime(log.getCreateTime())
                        .build())
                .collect(Collectors.toList());
        return ApiResponse.success(UserAdminDTO.AuditPage.builder()
                .total(p.getTotal())
                .items(items)
                .build());
    }

    private UserAdminDTO.UserAdminItem toAdminItem(User user) {
        Date lastActive = userTaskInstanceMapper.selectLastActiveTimeByUserId(user.getId());
        return UserAdminDTO.UserAdminItem.builder()
                .id(user.getId())
                .username(user.getUsername())
                .roles(user.getRoles())
                .pointBalance(user.getPointBalance())
                .enabled(isEnabled(user.getRoles()))
                .registerTime(user.getCreateTime())
                .lastActiveTime(lastActive)
                .build();
    }

    private boolean isEnabled(String roles) {
        if (roles == null) {
            return false;
        }
        return !roles.toUpperCase().contains("ROLE_DISABLED");
    }

    private boolean inActiveRange(Date lastActive, String activeStart, String activeEnd) {
        if ((activeStart == null || activeStart.trim().isEmpty()) && (activeEnd == null || activeEnd.trim().isEmpty())) {
            return true;
        }
        if (lastActive == null) {
            return false;
        }
        if (activeStart != null && !activeStart.trim().isEmpty()) {
            try {
                if (lastActive.before(java.sql.Timestamp.valueOf(activeStart.trim()))) {
                    return false;
                }
            } catch (Exception ignored) {
            }
        }
        if (activeEnd != null && !activeEnd.trim().isEmpty()) {
            try {
                if (lastActive.after(java.sql.Timestamp.valueOf(activeEnd.trim()))) {
                    return false;
                }
            } catch (Exception ignored) {
            }
        }
        return true;
    }

    private UserAdminDTO.PointSummary buildPointSummary(Long userId, Integer currentPoints) {
        List<UserRewardRecord> records = userRewardRecordService.selectByUserId(userId);
        long gain = 0L;
        long consume = 0L;
        long recent = 0L;
        Date now = new Date();
        long oneDayMs = 24L * 60 * 60 * 1000;
        for (UserRewardRecord r : records) {
            if (r == null || r.getRewardValue() == null) {
                continue;
            }
            int v = r.getRewardValue();
            if (v >= 0) {
                gain += v;
            } else {
                consume += Math.abs(v);
            }
            if (r.getCreateTime() != null && now.getTime() - r.getCreateTime().getTime() <= oneDayMs) {
                recent += v;
            }
        }
        return UserAdminDTO.PointSummary.builder()
                .currentPoints(currentPoints == null ? 0 : currentPoints)
                .totalGain(gain)
                .totalConsume(consume)
                .recentChange(recent)
                .build();
    }

    private UserAdminDTO.BehaviorSummary buildBehaviorSummary(Long userId, int days) {
        Calendar cal = Calendar.getInstance();
        Date end = cal.getTime();
        cal.add(Calendar.DAY_OF_MONTH, -days);
        Date start = cal.getTime();

        List<UserActionLog> actionLogs = userActionLogMapper.selectList(new QueryWrapper<UserActionLog>()
                .eq("user_id", userId)
                .ge("create_time", start)
                .le("create_time", end));
        long behaviorCount = actionLogs == null ? 0L : actionLogs.size();
        long signDays = actionLogs == null ? 0L : actionLogs.stream()
                .filter(Objects::nonNull)
                .filter(a -> a.getActionType() != null && a.getActionType().toUpperCase().contains("SIGN"))
                .map(a -> new java.text.SimpleDateFormat("yyyy-MM-dd").format(a.getCreateTime()))
                .distinct()
                .count();

        List<UserTaskInstance> tasks = userTaskInstanceMapper.selectByUserId(userId);
        List<UserTaskInstance> inWindowTasks = tasks == null ? List.of() : tasks.stream()
                .filter(t -> t != null && t.getCreateTime() != null && !t.getCreateTime().before(start) && !t.getCreateTime().after(end))
                .collect(Collectors.toList());
        long accepted = inWindowTasks.size();
        long completed = inWindowTasks.stream().filter(t -> t.getStatus() != null && t.getStatus() == 3).count();
        double completionRate = accepted == 0 ? 0D : round2(completed * 100D / accepted);

        List<UserRewardRecord> rewards = userRewardRecordService.selectByUserId(userId);
        long rewardCount = rewards == null ? 0L : rewards.stream()
                .filter(r -> r != null && r.getCreateTime() != null && !r.getCreateTime().before(start) && !r.getCreateTime().after(end))
                .count();
        double rewardRate = completed == 0 ? 0D : round2(rewardCount * 100D / completed);

        return UserAdminDTO.BehaviorSummary.builder()
                .days(days)
                .behaviorCount(behaviorCount)
                .signDays(signDays)
                .taskCompletionRate(completionRate)
                .rewardRate(rewardRate)
                .build();
    }

    private void writeAdminAudit(Authentication authentication, Long targetUserId, String actionType, String detail) {
        try {
            Long operatorId = null;
            if (authentication != null && authentication.getName() != null) {
                User operator = userService.selectByUsername(authentication.getName());
                operatorId = operator == null ? null : operator.getId();
            }
            UserActionLog audit = UserActionLog.builder()
                    .userId(operatorId)
                    .actionType(actionType + "|" + (detail == null ? "" : detail))
                    .actionValue(targetUserId == null ? 0 : targetUserId.intValue())
                    .createTime(new Date())
                    .build();
            userActionLogService.save(audit);
        } catch (Exception ignored) {
        }
    }

    private String extractAuditAction(String actionType) {
        if (actionType == null) {
            return null;
        }
        int idx = actionType.indexOf('|');
        return idx < 0 ? actionType : actionType.substring(0, idx);
    }

    private String extractAuditDetail(String actionType) {
        if (actionType == null) {
            return null;
        }
        int idx = actionType.indexOf('|');
        if (idx < 0 || idx + 1 >= actionType.length()) {
            return null;
        }
        return actionType.substring(idx + 1);
    }

    private double round2(double value) {
        return Math.round(value * 100D) / 100D;
    }
}
