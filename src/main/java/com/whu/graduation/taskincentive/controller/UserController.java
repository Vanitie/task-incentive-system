package com.whu.graduation.taskincentive.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.whu.graduation.taskincentive.dao.entity.User;
import com.whu.graduation.taskincentive.dto.ChartData;
import com.whu.graduation.taskincentive.dto.PageResult;
import com.whu.graduation.taskincentive.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 用户控制器
 */
@RestController
@RequestMapping("/api/user")
public class UserController {

    @Autowired
    private UserService userService;

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public User getById(@PathVariable Long id){
        return userService.getById(id);
    }

    @GetMapping("/list")
    @PreAuthorize("hasRole('ADMIN')")
    public PageResult<User> listAll(@RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int size){
        Page<User> p = new Page<>(page, size);
        p = userService.selectPage(p);
        return PageResult.<User>builder().total(p.getTotal()).page((int)p.getCurrent()).size((int)p.getSize()).items(p.getRecords()).build();
    }

    @PostMapping("/create")
    public boolean create(@RequestBody User user){
        return userService.save(user);
    }

    @PutMapping("/update")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public boolean update(@RequestBody User user){
        return userService.update(user);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public boolean delete(@PathVariable Long id){
        return userService.deleteById(id);
    }

    @PostMapping("/points")
    @PreAuthorize("hasRole('ADMIN')")
    public boolean updatePoints(@RequestParam Long userId, @RequestParam Integer points){
        return userService.updateUserPoints(userId, points);
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
    public ChartData countTotalUsers(){
        List<Long> data = userService.getUserCountLast7Days();
        long value = userService.countAllUsers();
        String percent = computePercent(data);
        return ChartData.builder()
                .name("用户总数")
                .value(value)
                .data(data)
                .percent(percent)
                .bgColor("#effaff")
                .color("#41b6ff")
                .duration(2200)
                .build();
    }

    /**
     * 统计过去7天内的活跃用户数（每天去重 user_id）
     */
    @GetMapping("/count/active7days")
    @PreAuthorize("hasAnyRole('ADMIN')")
    public ChartData countActive7Days(){
        List<Long> data = userService.getActiveUserCountLast7Days();
        long value = data.stream().mapToLong(Long::longValue).sum();
        String percent = computePercent(data);
        return ChartData.builder()
                .name("活跃用户数")
                .value(value)
                .data(data)
                .percent(percent)
                .bgColor("#fff5f4")
                .color("#e85f33")
                .duration(1600)
                .build();
    }

    /**
     * 统计过去7天内每日接取任务的用户数（活跃用户数）
     */
    @GetMapping("/count/today")
    @PreAuthorize("hasAnyRole('ADMIN')")
    public ChartData countToday(){
        List<Long> data = userService.getActiveUserCountLast7Days();
        long value = data.stream().mapToLong(Long::longValue).sum();
        String percent = computePercent(data);
        return ChartData.builder()
                .name("今日参与任务用户数")
                .value(value)
                .data(data)
                .percent(percent)
                .bgColor("#eff8f4")
                .color("#26ce83")
                .duration(1500)
                .build();
    }
}
