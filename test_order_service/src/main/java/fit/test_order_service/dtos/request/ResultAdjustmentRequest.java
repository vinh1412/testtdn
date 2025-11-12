/*
 * @ (#) ResultAdjustmentRequest.java    1.0    29/10/2025
 * Copyright (c) 2025 IUH. All rights reserved.
 */
package fit.test_order_service.dtos.request;/*
 * @description:
 * @author: Bao Thong
 * @date: 29/10/2025
 * @version: 1.0
 */

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ResultAdjustmentRequest {
    @NotBlank(message = "Result ID is required")
    private String resultId;

    @NotBlank(message = "New value text is required")
    @Size(max = 64, message = "Value text cannot exceed 64 characters")
    private String newValueText;

    @Size(max = 255, message = "Note cannot exceed 255 characters")
    private String note;
}
