/*
 * @ (#) FileStorageService.java    1.0    22/10/2025
 * Copyright (c) 2025 IUH. All rights reserved.
 */
package fit.test_order_service.services;/*
 * @description:
 * @author: Bao Thong
 * @date: 22/10/2025
 * @version: 1.0
 */

import fit.test_order_service.enums.StorageType;

public interface FileStorageService {

    /**
     * Lưu trữ file.
     *
     * @param fileBytes      Nội dung file dạng byte array.
     * @param directoryPath  Đường dẫn thư mục mong muốn (có thể null, khi đó dùng default).
     * @param fileName       Tên file mong muốn.
     * @param contentType    Loại MIME của file (vd: "application/pdf").
     * @param uploaderUserId ID người upload/tạo file.
     * @return Đường dẫn đầy đủ (absolute path) của file đã lưu.
     * @throws RuntimeException nếu có lỗi xảy ra.
     */
    String storeFile(byte[] fileBytes, String directoryPath, String fileName, String contentType, String uploaderUserId);

    /**
     * Lấy loại hình lưu trữ đang được sử dụng (LOCAL, S3,...).
     */
    StorageType getStorageType();
}
