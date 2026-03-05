package com.whu.graduation.taskincentive.controller;

import com.whu.graduation.taskincentive.dao.entity.User;
import com.whu.graduation.taskincentive.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
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
    public User getById(@PathVariable Long id){
        return userService.getById(id);
    }

    @GetMapping("/list")
    public List<User> listAll(){
        return userService.listAll();
    }

    @PostMapping("/create")
    public boolean create(@RequestBody User user){
        return userService.save(user);
    }

    @PutMapping("/update")
    public boolean update(@RequestBody User user){
        return userService.update(user);
    }

    @DeleteMapping("/{id}")
    public boolean delete(@PathVariable Long id){
        return userService.deleteById(id);
    }

    @PostMapping("/points")
    public boolean updatePoints(@RequestParam Long userId, @RequestParam Integer points){
        return userService.updateUserPoints(userId, points);
    }
}
