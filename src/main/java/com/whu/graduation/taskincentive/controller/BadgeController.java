package com.whu.graduation.taskincentive.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.whu.graduation.taskincentive.dao.entity.Badge;
import com.whu.graduation.taskincentive.dto.ApiResponse;
import com.whu.graduation.taskincentive.dto.PageResult;
import com.whu.graduation.taskincentive.service.BadgeImageStorageService;
import com.whu.graduation.taskincentive.service.BadgeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * 徽章控制器
 */
@RestController
@RequestMapping("/api/badge")
public class BadgeController {

    @Autowired
    private BadgeService badgeService;

    @Autowired
    private BadgeImageStorageService badgeImageStorageService;

    @GetMapping("/list")
    public ApiResponse<PageResult<Badge>> listAll(@RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int size){
        Page<Badge> p = new Page<>(page, size);
        p = badgeService.selectPage(p);
        PageResult<Badge> pr = PageResult.<Badge>builder().total(p.getTotal()).page((int)p.getCurrent()).size((int)p.getSize()).items(p.getRecords()).build();
        return ApiResponse.success(pr);
    }

    @GetMapping("/{id}")
    public ApiResponse<Badge> get(@PathVariable Long id){
        return ApiResponse.success(badgeService.getById(id));
    }

    @PostMapping("/create")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Boolean> create(@RequestBody Badge badge){
        return ApiResponse.success(badgeService.save(badge));
    }

    @PutMapping("/update")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Boolean> update(@RequestBody Badge badge){
        return ApiResponse.success(badgeService.update(badge));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Boolean> delete(@PathVariable Long id){
        return ApiResponse.success(badgeService.deleteById(id));
    }

    /**
     * 按名称模糊搜索徽章
     */
    @GetMapping("/search")
    public ApiResponse<PageResult<Badge>> searchByName(@RequestParam String name, @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int size) {
        Page<Badge> p = new Page<>(page, size);
        p = badgeService.searchByName(name, p);
        PageResult<Badge> pr = PageResult.<Badge>builder().total(p.getTotal()).page((int)p.getCurrent()).size((int)p.getSize()).items(p.getRecords()).build();
        return ApiResponse.success(pr);
    }

    /**
     * 上传徽章图片，返回可访问 URL。可选 badgeId 用于立即回填 imageUrl。
     */
    @PostMapping("/upload-image")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<String> uploadImage(@RequestParam("file") MultipartFile file,
                                           @RequestParam(required = false) Long badgeId) {
        BadgeImageStorageService.StoredBadgeImage stored = badgeImageStorageService.store(file);
        String relativePath = "/api/badge/image/" + stored.getFileName();
        String url = ServletUriComponentsBuilder.fromCurrentContextPath().path(relativePath).toUriString();

        if (badgeId != null) {
            Badge badge = badgeService.getById(badgeId);
            if (badge == null) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "badge not found: " + badgeId);
            }
            badge.setImageUrl(url);
            badgeService.update(badge);
        }
        return ApiResponse.success(url);
    }

    /**
     * 公开读取徽章图片。
     */
    @GetMapping("/image/{fileName:.+}")
    public ResponseEntity<Resource> getImage(@PathVariable String fileName) {
        BadgeImageStorageService.StoredBadgeImage stored = badgeImageStorageService.load(fileName);
        MediaType mediaType = stored.getMediaType() == null ? MediaType.APPLICATION_OCTET_STREAM : stored.getMediaType();
        return ResponseEntity.ok().contentType(mediaType).body(stored.getResource());
    }
}
