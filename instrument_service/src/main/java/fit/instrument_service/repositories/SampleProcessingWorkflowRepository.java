/*
 * @ {#} SampleProcessingWorkflowRepository.java   1.0     12/11/2025
 *
 * Copyright (c) 2025 IUH. All rights reserved.
 */

package fit.instrument_service.repositories;

import fit.instrument_service.entities.SampleProcessingWorkflow;
import fit.instrument_service.enums.WorkflowStatus;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * @description: Repository interface for managing SampleProcessingWorkflow entities in MongoDB.
 * @author: GitHub Copilot
 * @date:   12/11/2025
 * @version:    1.0
 */
@Repository
public interface SampleProcessingWorkflowRepository extends MongoRepository<SampleProcessingWorkflow, String> {
    
    List<SampleProcessingWorkflow> findByInstrumentIdAndStatus(String instrumentId, WorkflowStatus status);
    
    List<SampleProcessingWorkflow> findByStatus(WorkflowStatus status);
    
    Optional<SampleProcessingWorkflow> findByCassetteId(String cassetteId);
}
