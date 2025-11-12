/*
 * @ {#} SampleAnalysisWorkflowServiceImpl.java   1.0     12/11/2025
 *
 * Copyright (c) 2025 IUH. All rights reserved.
 */

package fit.instrument_service.services.impl;

import feign.FeignException;
import fit.instrument_service.client.TestOrderFeignClient;
import fit.instrument_service.dtos.request.InitiateWorkflowRequest;
import fit.instrument_service.dtos.request.SampleInput;
import fit.instrument_service.dtos.response.SampleResponse;
import fit.instrument_service.dtos.response.WorkflowResponse;
import fit.instrument_service.entities.*;
import fit.instrument_service.enums.*;
import fit.instrument_service.exceptions.NotFoundException;
import fit.instrument_service.repositories.*;
import fit.instrument_service.services.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @description: Implementation of SampleAnalysisWorkflowService
 * @author: GitHub Copilot
 * @date:   12/11/2025
 * @version:    1.0
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SampleAnalysisWorkflowServiceImpl implements SampleAnalysisWorkflowService {
    
    private final InstrumentRepository instrumentRepository;
    private final BloodSampleRepository bloodSampleRepository;
    private final SampleProcessingWorkflowRepository workflowRepository;
    private final CassetteRepository cassetteRepository;
    private final BarcodeValidationService barcodeValidationService;
    private final ReagentCheckService reagentCheckService;
    private final NotificationService notificationService;
    private final TestOrderFeignClient testOrderFeignClient;
    
    @Override
    @Transactional
    public WorkflowResponse initiateWorkflow(InitiateWorkflowRequest request) {
        log.info("Initiating workflow for instrument: {}", request.getInstrumentId());
        
        // Verify instrument exists
        Instrument instrument = instrumentRepository.findById(request.getInstrumentId())
                .orElseThrow(() -> new NotFoundException("Instrument not found: " + request.getInstrumentId()));
        
        // Check if instrument is available
        if (instrument.getStatus() != InstrumentStatus.AVAILABLE) {
            throw new IllegalStateException("Instrument is not available for workflow execution");
        }
        
        // Check reagent levels
        boolean reagentsAreSufficient = reagentCheckService.areReagentsSufficient(request.getInstrumentId());
        if (!reagentsAreSufficient) {
            log.error("Insufficient reagents for instrument: {}", request.getInstrumentId());
            notificationService.notifyInsufficientReagents(request.getInstrumentId());
            throw new IllegalStateException("Insufficient reagent levels. Workflow halted.");
        }
        
        // Create workflow
        SampleProcessingWorkflow workflow = new SampleProcessingWorkflow();
        workflow.setInstrumentId(request.getInstrumentId());
        workflow.setCassetteId(request.getCassetteId());
        workflow.setStatus(WorkflowStatus.INITIATED);
        workflow.setStartedAt(LocalDateTime.now());
        workflow.setReagentCheckPassed(true);
        workflow.setTestOrderServiceAvailable(true);
        workflow = workflowRepository.save(workflow);
        
        log.info("Created workflow: {}", workflow.getId());
        
        // Process and validate samples
        List<String> sampleIds = new ArrayList<>();
        for (SampleInput sampleInput : request.getSamples()) {
            BloodSample sample = processSampleInput(sampleInput, workflow.getId(), request.getInstrumentId());
            sampleIds.add(sample.getId());
        }
        
        // Update workflow with sample IDs
        workflow.setSampleIds(sampleIds);
        workflow.setStatus(WorkflowStatus.VALIDATING);
        workflow = workflowRepository.save(workflow);
        
        // Change instrument status to RUNNING
        instrument.setStatus(InstrumentStatus.RUNNING);
        instrumentRepository.save(instrument);
        
        // Start processing
        executeWorkflow(workflow, instrument);
        
        return buildWorkflowResponse(workflow);
    }
    
    private BloodSample processSampleInput(SampleInput input, String workflowId, String instrumentId) {
        log.info("Processing sample input with barcode: {}", input.getBarcode());
        
        BloodSample sample = new BloodSample();
        sample.setBarcode(input.getBarcode());
        sample.setWorkflowId(workflowId);
        sample.setInstrumentId(instrumentId);
        sample.setCassetteId(input.getCassetteId());
        sample.setStatus(SampleStatus.PENDING);
        
        // Validate barcode
        if (!barcodeValidationService.isValidBarcode(input.getBarcode())) {
            log.warn("Invalid barcode: {}", input.getBarcode());
            sample.setStatus(SampleStatus.SKIPPED);
            sample.setSkipReason("Invalid barcode format");
            sample = bloodSampleRepository.save(sample);
            notificationService.notifySampleStatusUpdate(sample);
            return sample;
        }
        
        // Handle test order
        if (StringUtils.hasText(input.getTestOrderId())) {
            // Test order provided, verify it exists
            try {
                testOrderFeignClient.getTestOrderById(input.getTestOrderId());
                sample.setTestOrderId(input.getTestOrderId());
                sample.setTestOrderAutoCreated(false);
            } catch (FeignException e) {
                log.warn("Test order not found: {}", input.getTestOrderId());
                // Create new test order
                String newTestOrderId = createTestOrder(input.getBarcode());
                sample.setTestOrderId(newTestOrderId);
                sample.setTestOrderAutoCreated(true);
            }
        } else {
            // No test order provided, create new one
            log.info("No test order provided for barcode: {}. Creating new test order.", input.getBarcode());
            String newTestOrderId = createTestOrder(input.getBarcode());
            sample.setTestOrderId(newTestOrderId);
            sample.setTestOrderAutoCreated(true);
        }
        
        sample.setStatus(SampleStatus.VALIDATED);
        sample = bloodSampleRepository.save(sample);
        notificationService.notifySampleStatusUpdate(sample);
        
        return sample;
    }
    
    private String createTestOrder(String barcode) {
        log.info("Creating new test order for barcode: {}", barcode);
        
        try {
            Map<String, Object> testOrderData = new HashMap<>();
            testOrderData.put("barcode", barcode);
            testOrderData.put("autoCreated", true);
            testOrderData.put("requiresPatientMatch", true);
            
            var response = testOrderFeignClient.createTestOrder(testOrderData);
            String testOrderId = (String) response.getData().get("orderId");
            log.info("Created test order: {} for barcode: {}", testOrderId, barcode);
            return testOrderId;
        } catch (FeignException e) {
            log.error("Failed to create test order for barcode: {}", barcode, e);
            // Return a placeholder ID to continue processing
            return "PENDING_" + UUID.randomUUID().toString();
        }
    }
    
    private void executeWorkflow(SampleProcessingWorkflow workflow, Instrument instrument) {
        log.info("Executing workflow: {}", workflow.getId());
        
        try {
            workflow.setStatus(WorkflowStatus.RUNNING);
            workflowRepository.save(workflow);
            
            // Get all validated samples
            List<BloodSample> samples = bloodSampleRepository.findByWorkflowId(workflow.getId());
            List<BloodSample> validatedSamples = samples.stream()
                    .filter(s -> s.getStatus() == SampleStatus.VALIDATED)
                    .collect(Collectors.toList());
            
            // Queue samples for processing
            for (BloodSample sample : validatedSamples) {
                sample.setStatus(SampleStatus.QUEUED);
                bloodSampleRepository.save(sample);
                notificationService.notifySampleStatusUpdate(sample);
            }
            
            // Process each sample
            for (BloodSample sample : validatedSamples) {
                processSample(sample);
            }
            
            // Complete workflow
            workflow.setStatus(WorkflowStatus.COMPLETED);
            workflow.setCompletedAt(LocalDateTime.now());
            workflow.setResultsConvertedToHl7(true);
            workflow.setResultsPublished(true);
            workflowRepository.save(workflow);
            
            // Change instrument status back to AVAILABLE
            instrument.setStatus(InstrumentStatus.AVAILABLE);
            instrumentRepository.save(instrument);
            
            notificationService.notifyWorkflowCompletion(workflow.getId(), instrument.getId());
            
            log.info("Workflow completed: {}", workflow.getId());
            
            // Check for next cassette
            processNextCassette(instrument.getId());
            
        } catch (Exception e) {
            log.error("Workflow execution failed: {}", workflow.getId(), e);
            workflow.setStatus(WorkflowStatus.FAILED);
            workflow.setErrorMessage(e.getMessage());
            workflowRepository.save(workflow);
            
            instrument.setStatus(InstrumentStatus.ERROR);
            instrumentRepository.save(instrument);
        }
    }
    
    private void processSample(BloodSample sample) {
        log.info("Processing sample: {}", sample.getBarcode());
        
        sample.setStatus(SampleStatus.PROCESSING);
        bloodSampleRepository.save(sample);
        notificationService.notifySampleStatusUpdate(sample);
        
        // Simulate analysis
        try {
            Thread.sleep(100); // Simulate processing time
            
            // Convert results to HL7 and publish
            String hl7Message = convertToHL7(sample);
            publishResults(hl7Message, sample);
            
            sample.setStatus(SampleStatus.COMPLETED);
            bloodSampleRepository.save(sample);
            notificationService.notifySampleStatusUpdate(sample);
            
            log.info("Sample processing completed: {}", sample.getBarcode());
        } catch (Exception e) {
            log.error("Sample processing failed: {}", sample.getBarcode(), e);
            sample.setStatus(SampleStatus.FAILED);
            bloodSampleRepository.save(sample);
            notificationService.notifySampleStatusUpdate(sample);
        }
    }
    
    private String convertToHL7(BloodSample sample) {
        log.debug("Converting sample results to HL7 format: {}", sample.getBarcode());
        // TODO: Implement actual HL7 conversion (Section 3.6.1.7)
        return "HL7|" + sample.getBarcode() + "|" + sample.getTestOrderId();
    }
    
    private void publishResults(String hl7Message, BloodSample sample) {
        log.info("Publishing HL7 results for sample: {}", sample.getBarcode());
        // TODO: Implement actual publishing via RabbitMQ or similar
    }
    
    @Override
    public WorkflowResponse processNextCassette(String instrumentId) {
        log.info("Processing next cassette for instrument: {}", instrumentId);
        
        // Find next unprocessed cassette
        List<Cassette> unprocessedCassettes = cassetteRepository
                .findByInstrumentIdAndProcessedOrderByQueuePositionAsc(instrumentId, false);
        
        if (unprocessedCassettes.isEmpty()) {
            log.info("No cassettes in queue for instrument: {}", instrumentId);
            return null;
        }
        
        Cassette nextCassette = unprocessedCassettes.get(0);
        log.info("Found next cassette: {}", nextCassette.getCassetteIdentifier());
        
        // Get samples for this cassette
        List<BloodSample> cassetteSamples = bloodSampleRepository.findAll().stream()
                .filter(s -> nextCassette.getCassetteIdentifier().equals(s.getCassetteId()))
                .filter(s -> s.getStatus() == SampleStatus.PENDING)
                .collect(Collectors.toList());
        
        if (cassetteSamples.isEmpty()) {
            log.warn("No pending samples for cassette: {}", nextCassette.getCassetteIdentifier());
            nextCassette.setProcessed(true);
            nextCassette.setProcessedAt(LocalDateTime.now());
            cassetteRepository.save(nextCassette);
            return processNextCassette(instrumentId);
        }
        
        // Create workflow for this cassette
        InitiateWorkflowRequest request = new InitiateWorkflowRequest();
        request.setInstrumentId(instrumentId);
        request.setCassetteId(nextCassette.getCassetteIdentifier());
        
        List<SampleInput> sampleInputs = cassetteSamples.stream()
                .map(s -> {
                    SampleInput input = new SampleInput();
                    input.setBarcode(s.getBarcode());
                    input.setTestOrderId(s.getTestOrderId());
                    input.setCassetteId(s.getCassetteId());
                    return input;
                })
                .collect(Collectors.toList());
        request.setSamples(sampleInputs);
        
        // Mark cassette as processed
        nextCassette.setProcessed(true);
        nextCassette.setProcessedAt(LocalDateTime.now());
        cassetteRepository.save(nextCassette);
        
        return initiateWorkflow(request);
    }
    
    @Override
    public WorkflowResponse getWorkflowStatus(String workflowId) {
        SampleProcessingWorkflow workflow = workflowRepository.findById(workflowId)
                .orElseThrow(() -> new NotFoundException("Workflow not found: " + workflowId));
        return buildWorkflowResponse(workflow);
    }
    
    @Override
    public List<SampleResponse> getWorkflowSamples(String workflowId) {
        List<BloodSample> samples = bloodSampleRepository.findByWorkflowId(workflowId);
        return samples.stream()
                .map(this::buildSampleResponse)
                .collect(Collectors.toList());
    }
    
    private WorkflowResponse buildWorkflowResponse(SampleProcessingWorkflow workflow) {
        return WorkflowResponse.builder()
                .workflowId(workflow.getId())
                .instrumentId(workflow.getInstrumentId())
                .cassetteId(workflow.getCassetteId())
                .status(workflow.getStatus())
                .sampleIds(workflow.getSampleIds())
                .startedAt(workflow.getStartedAt())
                .completedAt(workflow.getCompletedAt())
                .reagentCheckPassed(workflow.isReagentCheckPassed())
                .testOrderServiceAvailable(workflow.isTestOrderServiceAvailable())
                .errorMessage(workflow.getErrorMessage())
                .build();
    }
    
    private SampleResponse buildSampleResponse(BloodSample sample) {
        return SampleResponse.builder()
                .sampleId(sample.getId())
                .barcode(sample.getBarcode())
                .testOrderId(sample.getTestOrderId())
                .workflowId(sample.getWorkflowId())
                .instrumentId(sample.getInstrumentId())
                .status(sample.getStatus())
                .isTestOrderAutoCreated(sample.isTestOrderAutoCreated())
                .skipReason(sample.getSkipReason())
                .build();
    }
}
