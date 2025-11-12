/*
 * @ (#) LocalFileStorageServiceImpl.java    1.0    22/10/2025
 * Copyright (c) 2025 IUH. All rights reserved.
 */
package fit.test_order_service.services.impl;/*
 * @description:
 * @author: Bao Thong
 * @date: 22/10/2025
 * @version: 1.0
 */

import fit.test_order_service.enums.StorageType;
import fit.test_order_service.services.FileStorageService;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

@Service
@Slf4j
public class LocalFileStorageServiceImpl implements FileStorageService {

    @Value("${file.storage.local.path:/tmp/test_order_files}")
    private String defaultStoragePath;

    private Path defaultFileStorageLocation;

    @PostConstruct
    public void init() {
        try {
            // Khởi tạo và tạo thư mục lưu trữ MẶC ĐỊNH
            defaultFileStorageLocation = Paths.get(defaultStoragePath).toAbsolutePath().normalize();
            Files.createDirectories(defaultFileStorageLocation);
            log.info("Initialized default local file storage at: {}", defaultFileStorageLocation);
        } catch (Exception ex) {
            // Nếu không tạo được thư mục mặc định, báo lỗi nghiêm trọng
            throw new RuntimeException("Could not create the default directory where files will be stored.", ex);
        }
    }

    @Override
    public String storeFile(byte[] fileBytes, String requestedDirectoryPath, String requestedFileName, String contentType, String uploaderUserId) {

        // 1. Xác định thư mục lưu trữ
        Path saveDirectory;
        if (requestedDirectoryPath != null && !requestedDirectoryPath.isBlank()) {
            saveDirectory = Paths.get(requestedDirectoryPath).toAbsolutePath().normalize();
            if (saveDirectory.toString().contains("..")) {
                log.error("Path traversal attempt detected. User: {}, Path: {}", uploaderUserId, requestedDirectoryPath);
                throw new SecurityException("Invalid path: '..' sequence is not allowed.");
            }
        } else {
            saveDirectory = this.defaultFileStorageLocation;
        }

        // 3. Tạo thư mục nếu nó không tồn tại
        try {
            if (!Files.exists(saveDirectory)) {
                Files.createDirectories(saveDirectory);
                log.info("Created user-specified directory: {}", saveDirectory);
            }
        } catch (IOException ex) {
            log.error("Could not create directory: {}. Error: {}", saveDirectory, ex.getMessage(), ex);
            throw new RuntimeException("Could not create directory for file storage.", ex);
        }

        // 4. Làm sạch tên file
        String sanitizedFileName = requestedFileName.replaceAll("[^a-zA-Z0-9\\s\\-_.]+", "_");
        String baseName = sanitizedFileName;
        String extension = "";
        int lastDot = sanitizedFileName.lastIndexOf('.');
        if (lastDot >= 0) {
            baseName = sanitizedFileName.substring(0, lastDot);
            extension = sanitizedFileName.substring(lastDot);
        }
        if (baseName.length() > 100) {
            baseName = baseName.substring(0, 100);
        }
        sanitizedFileName = baseName + extension;

        // 5. Xử lý trùng tên
        Path targetLocation = saveDirectory.resolve(sanitizedFileName).normalize();
        int counter = 1;

        while (Files.exists(targetLocation)) {
            String newBaseName = baseName + "(" + counter + ")";
            if ((newBaseName + extension).length() > 120) {
                log.warn("Generated filename too long after adding counter: {}. Falling back to UUID.", newBaseName + extension);
                String uuidFileName = UUID.randomUUID() + extension;
                targetLocation = saveDirectory.resolve(uuidFileName).normalize(); // Dùng saveDirectory
                sanitizedFileName = uuidFileName;
                break;
            }

            targetLocation = saveDirectory.resolve(newBaseName + extension);

            sanitizedFileName = newBaseName + extension;
            counter++;
        }

        // 6. Ghi file
        try {
            Files.write(targetLocation, fileBytes);
            log.info("Stored file {} locally at {}", sanitizedFileName, targetLocation);
            return targetLocation.toString();
        } catch (IOException ex) {
            log.error("Could not store file {}. Error: {}", requestedFileName, ex.getMessage(), ex);
            throw new RuntimeException("Could not store file " + requestedFileName + ". Please try again!", ex);
        }
    }

    @Override
    public StorageType getStorageType() {
        return StorageType.LOCAL;
    }
}
