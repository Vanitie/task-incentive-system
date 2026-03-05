package com.whu.graduation.taskincentive.controller;

import com.whu.graduation.taskincentive.dao.entity.Badge;
import com.whu.graduation.taskincentive.service.BadgeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 徽章控制器
 */
@RestController
@RequestMapping("/api/badge")
public class BadgeController {

    @Autowired
    private BadgeService badgeService;

    @GetMapping("/list")
    public List<Badge> listAll(){
        return badgeService.listAll();
    }

    @GetMapping("/{id}")
    public Badge get(@PathVariable Long id){
        return badgeService.getById(id);
    }

    @PostMapping("/create")
    public boolean create(@RequestBody Badge badge){
        return badgeService.save(badge);
    }

    @PutMapping("/update")
    public boolean update(@RequestBody Badge badge){
        return badgeService.update(badge);
    }

    @DeleteMapping("/{id}")
    public boolean delete(@PathVariable Long id){
        return badgeService.deleteById(id);
    }
}
