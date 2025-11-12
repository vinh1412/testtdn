/*
 * @ (#) WorkflowStatus.java    1.0    12/11/2025
 * Copyright (c) 2025 IUH. All rights reserved.
 */
package fit.instrument_service.enums;

/**
 * @description: Enum for workflow execution status
 * @author: GitHub Copilot
 * @date: 12/11/2025
 * @version: 1.0
 */
public enum WorkflowStatus {
    INITIATED,          // Workflow started
    VALIDATING,         // Validating samples and reagents
    RUNNING,            // Analysis in progress
    COMPLETED,          // All samples processed successfully
    FAILED,             // Workflow failed
    HALTED              // Workflow halted due to insufficient reagents or error
}
