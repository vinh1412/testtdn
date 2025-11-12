/*
 * @ (#) SampleStatus.java    1.0    12/11/2025
 * Copyright (c) 2025 IUH. All rights reserved.
 */
package fit.instrument_service.enums;

/**
 * @description: Enum for blood sample status in the analysis workflow
 * @author: GitHub Copilot
 * @date: 12/11/2025
 * @version: 1.0
 */
public enum SampleStatus {
    PENDING,        // Sample input into system, awaiting processing
    VALIDATED,      // Barcode and test order validated
    QUEUED,         // Sample queued for analysis
    PROCESSING,     // Sample currently being analyzed
    COMPLETED,      // Analysis completed successfully
    SKIPPED,        // Sample skipped due to invalid barcode
    FAILED          // Analysis failed
}
