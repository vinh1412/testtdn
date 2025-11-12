/*
 * @ {#} SampleAnalysisWorkflowService.java   1.0     12/11/2025
 *
 * Copyright (c) 2025 IUH. All rights reserved.
 */

package fit.instrument_service.services;

import fit.instrument_service.dtos.request.InitiateWorkflowRequest;
import fit.instrument_service.dtos.response.SampleResponse;
import fit.instrument_service.dtos.response.WorkflowResponse;

import java.util.List;

/**
 * @description: Service interface for orchestrating blood sample analysis workflow
 * @author: GitHub Copilot
 * @date:   12/11/2025
 * @version:    1.0
 */
public interface SampleAnalysisWorkflowService {
    
    /**
     * Initiate the sample analysis workflow
     *
     * @param request The workflow initiation request
     * @return The workflow response
     */
    WorkflowResponse initiateWorkflow(InitiateWorkflowRequest request);
    
    /**
     * Process the next cassette in queue for an instrument
     *
     * @param instrumentId The instrument ID
     * @return The workflow response, or null if no cassettes in queue
     */
    WorkflowResponse processNextCassette(String instrumentId);
    
    /**
     * Get workflow status
     *
     * @param workflowId The workflow ID
     * @return The workflow response
     */
    WorkflowResponse getWorkflowStatus(String workflowId);
    
    /**
     * Get samples in a workflow
     *
     * @param workflowId The workflow ID
     * @return List of sample responses
     */
    List<SampleResponse> getWorkflowSamples(String workflowId);
}
