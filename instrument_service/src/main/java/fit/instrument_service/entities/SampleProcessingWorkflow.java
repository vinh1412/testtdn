/*
 * @ (#) SampleProcessingWorkflow.java    1.0    12/11/2025
 * Copyright (c) 2025 IUH. All rights reserved.
 */
package fit.instrument_service.entities;

import fit.instrument_service.enums.WorkflowStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.LocalDateTime;
import java.util.List;

/**
 * @description: Workflow entity for tracking blood sample analysis execution
 * @author: GitHub Copilot
 * @date: 12/11/2025
 * @version: 1.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "sampleProcessingWorkflows")
public class SampleProcessingWorkflow extends BaseDocument {

    @Id
    private String id;

    @Field("instrument_id")
    @Indexed
    private String instrumentId; // Instrument performing the workflow

    @Field("cassette_id")
    private String cassetteId; // Cassette being processed

    @Field("status")
    private WorkflowStatus status; // Current workflow status

    @Field("sample_ids")
    private List<String> sampleIds; // List of sample IDs in this workflow

    @Field("started_at")
    private LocalDateTime startedAt; // Workflow start time

    @Field("completed_at")
    private LocalDateTime completedAt; // Workflow completion time

    @Field("reagent_check_passed")
    private boolean reagentCheckPassed = false; // Flag if reagent levels were sufficient

    @Field("test_order_service_available")
    private boolean testOrderServiceAvailable = true; // Flag if Test Order Service was available

    @Field("error_message")
    private String errorMessage; // Error message if workflow failed

    @Field("results_converted_to_hl7")
    private boolean resultsConvertedToHl7 = false; // Flag if results were converted to HL7

    @Field("results_published")
    private boolean resultsPublished = false; // Flag if results were published
}
