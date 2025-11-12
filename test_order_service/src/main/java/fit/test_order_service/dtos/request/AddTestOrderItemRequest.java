/*
 * @ (#) AddTestOrderItemRequest.java    1.0    16/10/2025
 * Copyright (c) 2025 IUH. All rights reserved.
 */
package fit.test_order_service.dtos.request;/*
 * @description:
 * @author: Bao Thong
 * @date: 16/10/2025
 * @version: 1.0
 */

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AddTestOrderItemRequest {
    @NotBlank(message = "Test name is required")
    private String testName;
}
