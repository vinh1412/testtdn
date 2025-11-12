/*
 * @ {#} UpdateTestOrderRequest.java   1.0     13/10/2025
 *
 * Copyright (c) 2025 IUH. All rights reserved.
 */

package fit.test_order_service.dtos.request;

import jakarta.validation.constraints.Pattern;
import lombok.Data;

/*
 * @description: Request DTO for updating a test order
 * @author: Tran Hien Vinh
 * @date:   13/10/2025
 * @version:    1.0
 */
@Data
public class UpdateTestOrderRequest {
    @Pattern(regexp = "^(PENDING|COMPLETED|CANCELLED)$",
            message = "Status must be one of: PENDING, COMPLETED, CANCELLED")
    private String status;

    @Pattern(regexp = "^(NONE|HUMAN_REVIEWED|AI_REVIEWED)$",
            message = "Review status must be one of: NONE, HUMAN_REVIEWED, AI_REVIEWED")
    private String reviewStatus;

    @Pattern(regexp = "^(HUMAN|AI)$",
            message = "Review mode must be one of: HUMAN, AI")
    private String reviewMode;
}
