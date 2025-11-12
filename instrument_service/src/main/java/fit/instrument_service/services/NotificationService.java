/*
 * @ {#} NotificationService.java   1.0     12/11/2025
 *
 * Copyright (c) 2025 IUH. All rights reserved.
 */

package fit.instrument_service.services;

import fit.instrument_service.entities.BloodSample;

/**
 * @description: Service interface for sending notifications
 * @author: GitHub Copilot
 * @date:   12/11/2025
 * @version:    1.0
 */
public interface NotificationService {
    
    /**
     * Send notification for sample status update
     *
     * @param sample The blood sample
     */
    void notifySampleStatusUpdate(BloodSample sample);
    
    /**
     * Send notification for workflow completion
     *
     * @param workflowId The workflow ID
     * @param instrumentId The instrument ID
     */
    void notifyWorkflowCompletion(String workflowId, String instrumentId);
    
    /**
     * Send notification for insufficient reagents
     *
     * @param instrumentId The instrument ID
     */
    void notifyInsufficientReagents(String instrumentId);
}
