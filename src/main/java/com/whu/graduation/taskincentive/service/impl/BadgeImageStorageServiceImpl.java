package com.whu.graduation.taskincentive.service.impl;

import com.whu.graduation.taskincentive.service.BadgeImageStorageService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
public class BadgeImageStorageServiceImpl implements BadgeImageStorageService {

    private static final Set<String> ALLOWED_EXT = Set.of("jpg", "jpeg", "png", "gif", "webp");

    private final Path baseDir;
    private final long maxSizeBytes;

    public BadgeImageStorageServiceImpl(
            @Value("${app.badge-image.upload-dir:./uploads/badge}") String uploadDir,
            @Value("${app.badge-image.max-size-mb:2}") int maxSizeMb) {
        this.baseDir = Paths.get(uploadDir).toAbsolutePath().normalize();
        this.maxSizeBytes = Math.max(1, maxSizeMb) * 1024L * 1024L;
    }

    @Override
    public StoredBadgeImage store(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(BAD_REQUEST, "empty file");
        }
        if (file.getSize() > maxSizeBytes) {
            throw new ResponseStatusException(BAD_REQUEST, "file too large");
        }

        String originalName = StringUtils.cleanPath(file.getOriginalFilename() == null ? "" : file.getOriginalFilename());
        String ext = getExtension(originalName);
        if (!ALLOWED_EXT.contains(ext)) {
            throw new ResponseStatusException(BAD_REQUEST, "unsupported file type");
        }

        String fileName = UUID.randomUUID().toString().replace("-", "") + "." + ext;
        try {
            Files.createDirectories(baseDir);
            Path target = baseDir.resolve(fileName).normalize();
            if (!target.startsWith(baseDir)) {
                throw new ResponseStatusException(BAD_REQUEST, "invalid file path");
            }
            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
            Resource resource = new UrlResource(target.toUri());
            MediaType mediaType = MediaTypeFactory.getMediaType(fileName).orElse(MediaType.APPLICATION_OCTET_STREAM);
            return new StoredBadgeImage(fileName, resource, mediaType);
        } catch (IOException e) {
            throw new ResponseStatusException(INTERNAL_SERVER_ERROR, "store image failed", e);
        }
    }

    @Override
    public StoredBadgeImage load(String fileName) {
        String cleaned = StringUtils.cleanPath(fileName == null ? "" : fileName);
        if (cleaned.contains("..")) {
            throw new ResponseStatusException(BAD_REQUEST, "invalid file name");
        }
        Path path = baseDir.resolve(cleaned).normalize();
        if (!path.startsWith(baseDir) || !Files.exists(path)) {
            throw new ResponseStatusException(NOT_FOUND, "image not found");
        }
        try {
            Resource resource = new UrlResource(path.toUri());
            MediaType mediaType = MediaTypeFactory.getMediaType(cleaned).orElse(MediaType.APPLICATION_OCTET_STREAM);
            return new StoredBadgeImage(cleaned, resource, mediaType);
        } catch (IOException e) {
            throw new ResponseStatusException(INTERNAL_SERVER_ERROR, "read image failed", e);
        }
    }

    private String getExtension(String name) {
        int idx = name.lastIndexOf('.');
        if (idx < 0 || idx == name.length() - 1) {
            return "";
        }
        return name.substring(idx + 1).toLowerCase(Locale.ROOT);
    }
}

