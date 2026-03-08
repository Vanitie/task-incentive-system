package com.whu.graduation.taskincentive.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.whu.graduation.taskincentive.dao.entity.User;
import com.whu.graduation.taskincentive.dto.PageResult;
import com.whu.graduation.taskincentive.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

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
}
