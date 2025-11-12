/*
 * @ (#) ReviewTestOrderRequest.java    1.0    29/10/2025
 * Copyright (c) 2025 IUH. All rights reserved.
 */
package fit.test_order_service.dtos.request;/*
 * @description:
 * @author: Bao Thong
 * @date: 29/10/2025
 * @version: 1.0
 */

import fit.test_order_service.enums.ReviewMode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class ReviewTestOrderRequest {

    // Mặc định là HUMAN nếu không cung cấp
    private ReviewMode reviewMode = ReviewMode.HUMAN;

    @Size(max = 500, message = "Overall review note cannot exceed 500 characters")
    private String note;

    @Valid // Đảm bảo các phần tử trong list cũng được validate
    private List<ResultAdjustmentRequest> adjustments;
}
