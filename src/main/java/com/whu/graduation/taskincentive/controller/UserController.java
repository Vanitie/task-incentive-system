package com.whu.graduation.taskincentive.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.whu.graduation.taskincentive.dao.entity.User;
import com.whu.graduation.taskincentive.dto.ApiResponse;
import com.whu.graduation.taskincentive.dto.ChartData;
import com.whu.graduation.taskincentive.dto.PageResult;
import com.whu.graduation.taskincentive.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

/**
 * 用户控制器
 */
@RestController
@RequestMapping("/api/user")
public class UserController {

    @Autowired
    private UserService userService;

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
        return ApiResponse.success(userService.updateUserPoints(userId, points));
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
}
