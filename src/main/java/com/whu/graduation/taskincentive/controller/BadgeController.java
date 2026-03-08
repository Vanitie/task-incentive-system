package com.whu.graduation.taskincentive.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.whu.graduation.taskincentive.dao.entity.Badge;
import com.whu.graduation.taskincentive.dto.PageResult;
import com.whu.graduation.taskincentive.service.BadgeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
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
    public PageResult<Badge> listAll(@RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int size){
        Page<Badge> p = new Page<>(page, size);
        p = badgeService.selectPage(p);
        return PageResult.<Badge>builder().total(p.getTotal()).page((int)p.getCurrent()).size((int)p.getSize()).items(p.getRecords()).build();
    }

    @GetMapping("/{id}")
    public Badge get(@PathVariable Long id){
        return badgeService.getById(id);
    }

    @PostMapping("/create")
    @PreAuthorize("hasRole('ADMIN')")
    public boolean create(@RequestBody Badge badge){
        return badgeService.save(badge);
    }

    @PutMapping("/update")
    @PreAuthorize("hasRole('ADMIN')")
    public boolean update(@RequestBody Badge badge){
        return badgeService.update(badge);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public boolean delete(@PathVariable Long id){
        return badgeService.deleteById(id);
    }
}
