/*
 * @ {#} UpdateTestOrderItemRequest.java   1.0     16/10/2025
 *
 * Copyright (c) 2025 IUH. All rights reserved.
 */

package fit.test_order_service.dtos.request;

import jakarta.validation.constraints.Pattern;
import lombok.Builder;
import lombok.Getter;

/*
 * @description: Request DTO for updating a test order item
 * @author: Tran Hien Vinh
 * @date:   16/10/2025
 * @version:    1.0
 */
@Builder
@Getter
public class UpdateTestOrderItemRequest {
    private String testName;

    @Pattern(regexp = "^(PENDING|COMPLETED|CANCELLED)$",
            message = "Status must be one of: PENDING, COMPLETED, CANCELLED")
    private String status;
}
