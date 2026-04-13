package com.whu.graduation.taskincentive.service;

import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.web.multipart.MultipartFile;

public interface BadgeImageStorageService {

    StoredBadgeImage store(MultipartFile file);

    StoredBadgeImage load(String fileName);

    class StoredBadgeImage {
        private final String fileName;
        private final Resource resource;
        private final MediaType mediaType;

        public StoredBadgeImage(String fileName, Resource resource, MediaType mediaType) {
            this.fileName = fileName;
            this.resource = resource;
            this.mediaType = mediaType;
        }

        public String getFileName() {
            return fileName;
        }

        public Resource getResource() {
            return resource;
        }

        public MediaType getMediaType() {
            return mediaType;
        }
    }
}

