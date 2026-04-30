package com.skep.storage;

import com.skep.common.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.UUID;

@Component
public class LocalDiskStorage implements FileStorage {

    private static final Logger log = LoggerFactory.getLogger(LocalDiskStorage.class);

    private final Path root;

    public LocalDiskStorage(@Value("${skep.storage.local.root:/app/uploads}") String rootPath) {
        this.root = Paths.get(rootPath).toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.root);
            log.info("LocalDiskStorage root: {}", this.root);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot create storage root: " + this.root, e);
        }
    }

    @Override
    public String store(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw ApiException.badRequest("EMPTY_FILE", "업로드된 파일이 비어있습니다");
        }
        LocalDate today = LocalDate.now();
        String key = String.format("%04d/%02d/%s.bin",
                today.getYear(), today.getMonthValue(), UUID.randomUUID());
        Path target = root.resolve(key).normalize();
        if (!target.startsWith(root)) {
            throw ApiException.badRequest("INVALID_KEY", "잘못된 저장 경로");
        }
        try {
            Files.createDirectories(target.getParent());
            try (var in = file.getInputStream()) {
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }
            return key;
        } catch (IOException e) {
            throw new IllegalStateException("file write failed: " + key, e);
        }
    }

    @Override
    public Resource load(String key) {
        Path target = root.resolve(key).normalize();
        if (!target.startsWith(root)) {
            throw ApiException.badRequest("INVALID_KEY", "잘못된 키");
        }
        try {
            UrlResource resource = new UrlResource(target.toUri());
            if (!resource.exists() || !resource.isReadable()) {
                throw ApiException.notFound("FILE_NOT_FOUND", "파일을 찾을 수 없습니다: " + key);
            }
            return resource;
        } catch (Exception e) {
            throw new IllegalStateException("file load failed: " + key, e);
        }
    }

    @Override
    public void delete(String key) {
        Path target = root.resolve(key).normalize();
        if (!target.startsWith(root)) return;
        try {
            Files.deleteIfExists(target);
        } catch (IOException e) {
            log.warn("file delete failed (ignored): {} — {}", key, e.getMessage());
        }
    }
}
