/*
 * @ (#) CreateTestOrderRequest.java    1.0    13/10/2025
 * Copyright (c) 2025 IUH. All rights reserved.
 */
package fit.test_order_service.dtos.request;/*
 * @description:
 * @author: Bao Thong
 * @date: 13/10/2025
 * @version: 1.0
 */

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateTestOrderRequest {

    @NotBlank(message = "Medical Record Code is required")
    private String medicalRecordCode;

}
