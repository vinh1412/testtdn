/*
 * @ {#} SampleAnalysisWorkflowServiceImpl.java   1.0     12/11/2025
 *
 * Copyright (c) 2025 IUH. All rights reserved.
 */

package fit.instrument_service.services.impl;

import ca.uhn.hl7v2.parser.Parser;
import feign.FeignException;
import fit.instrument_service.client.TestOrderFeignClient;
import fit.instrument_service.dtos.request.InitiateWorkflowRequest;
import fit.instrument_service.dtos.request.SampleInput;
import fit.instrument_service.dtos.response.SampleResponse;
import fit.instrument_service.dtos.response.WorkflowResponse;
import fit.instrument_service.entities.BloodSample;
import fit.instrument_service.entities.Cassette;
import fit.instrument_service.entities.Instrument;
import fit.instrument_service.entities.SampleProcessingWorkflow;
import fit.instrument_service.enums.InstrumentStatus;
import fit.instrument_service.enums.SampleStatus;
import fit.instrument_service.enums.WorkflowStatus;
import fit.instrument_service.exceptions.NotFoundException;
import fit.instrument_service.repositories.BloodSampleRepository;
import fit.instrument_service.repositories.CassetteRepository;
import fit.instrument_service.repositories.InstrumentRepository;
import fit.instrument_service.repositories.SampleProcessingWorkflowRepository;
import fit.instrument_service.services.BarcodeValidationService;
import fit.instrument_service.services.NotificationService;
import fit.instrument_service.services.ReagentCheckService;
import fit.instrument_service.services.SampleAnalysisWorkflowService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/*
 * @description: Service triển khai quy trình phân tích mẫu máu
 * @author: Tran Hien Vinh
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
    private final Parser parser;
    private final Random random = new Random();

    @Override
    @Transactional
    public WorkflowResponse initiateWorkflow(InitiateWorkflowRequest request) {
        log.info("Initiating workflow for instrument: {}", request.getInstrumentId());

        // Kiểm tra thiết bị tồn tại
        Instrument instrument = instrumentRepository.findById(request.getInstrumentId())
                .orElseThrow(() -> new NotFoundException("Instrument not found: " + request.getInstrumentId()));

        // Kiểm tra trạng thái thiết bị nếu không phải AVAILABLE thì không thể khởi tạo quy trình
        if (instrument.getStatus() != InstrumentStatus.AVAILABLE) {
            throw new IllegalStateException("Instrument is not available for workflow execution");
        }

        // Kiểm tra hóa chất, nếu không đủ thì thông báo và dừng quy trình
        boolean reagentsAreSufficient = reagentCheckService.areReagentsSufficient(request.getInstrumentId());
        if (!reagentsAreSufficient) {
            log.error("Insufficient reagents for instrument: {}", request.getInstrumentId());
            notificationService.notifyInsufficientReagents(request.getInstrumentId());
            throw new IllegalStateException("Insufficient reagent levels. Workflow halted.");
        }

        // Tạo quy trình mới và lưu vào cơ sở dữ liệu
        SampleProcessingWorkflow workflow = new SampleProcessingWorkflow();
        workflow.setInstrumentId(request.getInstrumentId());
        workflow.setCassetteId(request.getCassetteId());
        workflow.setStatus(WorkflowStatus.INITIATED);
        workflow.setStartedAt(LocalDateTime.now());
        workflow.setReagentCheckPassed(true);
        workflow.setTestOrderServiceAvailable(true);
        workflow = workflowRepository.save(workflow);

        log.info("Created workflow: {}", workflow.getId());

        // Xử lý từng mẫu trong yêu cầu
        List<String> sampleIds = new ArrayList<>();
        for (SampleInput sampleInput : request.getSamples()) {
            // Xử lý mẫu và lưu vào cơ sở dữ liệu
            BloodSample sample = processSampleInput(sampleInput, workflow.getId(), request.getInstrumentId());
            sampleIds.add(sample.getId());
        }

        // Cập nhật danh sách mẫu vào quy trình
        workflow.setSampleIds(sampleIds);
        workflow.setStatus(WorkflowStatus.VALIDATING);
        workflow = workflowRepository.save(workflow);

        // Cập nhật trạng thái thiết bị thành RUNNING
        instrument.setStatus(InstrumentStatus.RUNNING);
        instrumentRepository.save(instrument);

        // Bắt đầu thực thi quy trình
        executeWorkflow(workflow, instrument);

        return buildWorkflowResponse(workflow);
    }

    // Hàm xử lý từng mẫu đầu vào
    private BloodSample processSampleInput(SampleInput input, String workflowId, String instrumentId) {
        log.info("Processing sample input with barcode: {}", input.getBarcode());

        // Tạo mẫu mới
        BloodSample sample = new BloodSample();
        sample.setBarcode(input.getBarcode());
        sample.setWorkflowId(workflowId);
        sample.setInstrumentId(instrumentId);
        sample.setCassetteId(input.getCassetteId());
        sample.setStatus(SampleStatus.PENDING);

        // Xác thực mã vạch, nếu không hợp lệ thì đánh dấu bỏ qua
        if (!barcodeValidationService.isValidBarcode(input.getBarcode())) {
            log.warn("Invalid barcode: {}", input.getBarcode());
            sample.setStatus(SampleStatus.SKIPPED);
            sample.setSkipReason("Invalid barcode format");
            sample = bloodSampleRepository.save(sample);
            notificationService.notifySampleStatusUpdate(sample);
            return sample;
        }

        // Xử lý đơn hàng xét nghiệm
        if (StringUtils.hasText(input.getTestOrderId())) {
            // Kiểm tra đơn hàng xét nghiệm có tồn tại không, nếu không thì tạo mới
            log.info("Checking test order ID: {}", input.getTestOrderId());
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
            // Tạo mới đơn hàng xét nghiệm nếu không cung cấp
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

    // Hàm tạo đơn hàng xét nghiệm mới
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

    // Hàm thực thi quy trình
    private void executeWorkflow(SampleProcessingWorkflow workflow, Instrument instrument) {
        log.info("Executing workflow: {}", workflow.getId());

        try {
            workflow.setStatus(WorkflowStatus.RUNNING);
            workflowRepository.save(workflow);

            // Lấy danh sách mẫu đã được xác thực
            List<BloodSample> samples = bloodSampleRepository.findByWorkflowId(workflow.getId());
            List<BloodSample> validatedSamples = samples.stream()
                    .filter(s -> s.getStatus() == SampleStatus.VALIDATED)
                    .collect(Collectors.toList());

            // Đặt trạng thái mẫu thành QUEUED và thông báo
            for (BloodSample sample : validatedSamples) {
                sample.setStatus(SampleStatus.QUEUED);
                bloodSampleRepository.save(sample);
                notificationService.notifySampleStatusUpdate(sample);
            }

            // Xử lý từng mẫu
            for (BloodSample sample : validatedSamples) {
                processSample(sample);
            }

            // Cập nhật trạng thái quy trình thành COMPLETED
            workflow.setStatus(WorkflowStatus.COMPLETED);
            workflow.setCompletedAt(LocalDateTime.now());
            workflow.setResultsConvertedToHl7(true);
            workflow.setResultsPublished(true);
            workflowRepository.save(workflow);

            // Cập nhật trạng thái thiết bị thành AVAILABLE
            instrument.setStatus(InstrumentStatus.AVAILABLE);
            instrumentRepository.save(instrument);

            // Gửi thông báo hoàn thành quy trình
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

    // Hàm xử lý mẫ
    private void processSample(BloodSample sample) {
        log.info("Processing sample: {}", sample.getBarcode());

        // Đặt trạng thái mẫu thành PROCESSING và thông báo
        sample.setStatus(SampleStatus.PROCESSING);
        bloodSampleRepository.save(sample);
        notificationService.notifySampleStatusUpdate(sample);

        // Giả lập xử lý mẫu
        try {
            Thread.sleep(100); // Giả lập thời gian xử lý

            // Chuyển đổi kết quả sang định dạng HL7
            String hl7Message = convertToHL7(sample);

            // Xuất bản kết quả HL7
            publishResults(hl7Message, sample);

            // Cập nhật trạng thái mẫu thành COMPLETED và thông báo
            sample.setStatus(SampleStatus.COMPLETED);
            bloodSampleRepository.save(sample);
            notificationService.notifySampleStatusUpdate(sample);

            log.info("Sample processing completed: {}", sample.getBarcode());
        } catch (Exception e) {
            // Xử lý lỗi trong quá trình xử lý mẫu
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

        // Lấy cassette chưa được xử lý tiếp theo trong hàng đợi
        List<Cassette> unprocessedCassettes = cassetteRepository
                .findByInstrumentIdAndProcessedOrderByQueuePositionAsc(instrumentId, false);

        // Nếu không còn cassette nào thì trả về null
        if (unprocessedCassettes.isEmpty()) {
            log.info("No cassettes in queue for instrument: {}", instrumentId);
            return null;
        }

        // Lấy cassette tiếp theo
        Cassette nextCassette = unprocessedCassettes.get(0);
        log.info("Found next cassette: {}", nextCassette.getCassetteIdentifier());

        // Lấy các mẫu máu liên quan đến cassette này và đang ở trạng thái PENDING
        List<BloodSample> cassetteSamples = bloodSampleRepository.findAll().stream()
                .filter(s -> nextCassette.getCassetteIdentifier().equals(s.getCassetteId()))
                .filter(s -> s.getStatus() == SampleStatus.PENDING)
                .collect(Collectors.toList());

        // Nếu không có mẫu nào thì đánh dấu cassette là đã xử lý và tiếp tục với cassette tiếp theo
        if (cassetteSamples.isEmpty()) {
            log.warn("No pending samples for cassette: {}", nextCassette.getCassetteIdentifier());
            nextCassette.setProcessed(true);
            nextCassette.setProcessedAt(LocalDateTime.now());
            cassetteRepository.save(nextCassette);
            return processNextCassette(instrumentId);
        }

        // Tạo yêu cầu khởi tạo quy trình cho cassette này
        InitiateWorkflowRequest request = new InitiateWorkflowRequest();
        request.setInstrumentId(instrumentId);
        request.setCassetteId(nextCassette.getCassetteIdentifier());

        // Thêm các mẫu vào quy trình xử lý
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

        // Đánh dấu cassette là đã được xử lý
        nextCassette.setProcessed(true);
        nextCassette.setProcessedAt(LocalDateTime.now());
        cassetteRepository.save(nextCassette);

        // Khởi tạo quy trình cho cassette này
        return initiateWorkflow(request);
    }

    @Override
    public WorkflowResponse getWorkflowStatus(String workflowId) {
        // Lấy quy trình từ cơ sở dữ liệu
        SampleProcessingWorkflow workflow = workflowRepository.findById(workflowId)
                .orElseThrow(() -> new NotFoundException("Workflow not found: " + workflowId));
        return buildWorkflowResponse(workflow);
    }

    @Override
    public List<SampleResponse> getWorkflowSamples(String workflowId) {
        // Lấy danh sách mẫu từ cơ sở dữ liệu theo workflowId
        List<BloodSample> samples = bloodSampleRepository.findByWorkflowId(workflowId);
        return samples.stream()
                .map(this::buildSampleResponse)
                .collect(Collectors.toList());
    }

    // Hàm xây dựng phản hồi quy trình
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

    // Hàm xây dựng phản hồi mẫu
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
