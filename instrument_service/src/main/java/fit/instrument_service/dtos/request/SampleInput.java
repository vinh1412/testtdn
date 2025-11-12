/*
 * @ (#) SampleInput.java    1.0    12/11/2025
 * Copyright (c) 2025 IUH. All rights reserved.
 */
package fit.instrument_service.dtos.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @description: Request DTO for inputting a blood sample into the system
 * @author: GitHub Copilot
 * @date: 12/11/2025
 * @version: 1.0
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SampleInput {
    
    @NotBlank(message = "Barcode is required")
    private String barcode;
    
    private String testOrderId; // Optional, will be auto-created if missing and barcode is valid
    
    private String cassetteId;
}
