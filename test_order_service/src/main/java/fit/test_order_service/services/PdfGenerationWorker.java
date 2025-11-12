package fit.test_order_service.services;

import com.fasterxml.jackson.core.type.TypeReference;
// Import DTOs response mới của bạn
import fit.test_order_service.dtos.response.CommentOrderResponse;
import fit.test_order_service.entities.OrderComment; // Giữ lại nếu PdfGeneratorUtil VẪN dùng nó
import fit.test_order_service.entities.ReportFileStore;
import fit.test_order_service.entities.ReportJob;
import fit.test_order_service.entities.TestOrder;
import fit.test_order_service.entities.TestResult;
import fit.test_order_service.enums.JobStatus;
import fit.test_order_service.enums.OrderStatus;
import fit.test_order_service.repositories.ReportFileStoreRepository;
import fit.test_order_service.repositories.ReportJobRepository;
import fit.test_order_service.repositories.TestOrderRepository;
import fit.test_order_service.utils.PdfGeneratorUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.text.Normalizer;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class PdfGenerationWorker {

    private final ReportJobRepository reportJobRepository;
    private final TestOrderRepository testOrderRepository;
    private final ReportFileStoreRepository fileStoreRepository;
    private final ObjectMapper objectMapper;
    private final PdfGeneratorUtil pdfGeneratorUtil;
    private final FileStorageService fileStorageService;

    @Async
    @Transactional
    public void generatePdf(String jobId) {
        log.info("Starting PDF generation for job ID: {}", jobId);
        ReportJob job = reportJobRepository.findById(jobId)
                .orElse(null);

        if (job == null) {
            log.error("ReportJob not found for ID: {}", jobId);
            return;
        }

        String customSavePath = null;
        String customFileName = null;
        // --- SỬA 1: Chuẩn bị list comments ---
        List<CommentOrderResponse> comments = new ArrayList<>(); // Dùng DTO Response

        try {
            // 0. Parse Params
            if (job.getParamsJson() != null && !job.getParamsJson().isBlank()) {
                try {
                    // --- SỬA 2: Đổi TypeReference thành Map<String, Object> ---
                    Map<String, Object> params = objectMapper.readValue(job.getParamsJson(), new TypeReference<Map<String, Object>>() {
                    });

                    customFileName = (String) params.get("customFileName");
                    customSavePath = (String) params.get("customSavePath"); // Lấy đường dẫn lưu

                    // --- SỬA 3: Trích xuất comments từ params ---
                    if (params.containsKey("comments")) {
                        // ObjectMapper lưu trữ nó dưới dạng List<Map>, cần convert lại
                        List<?> rawComments = (List<?>) params.get("comments");
                        comments = objectMapper.convertValue(rawComments, new TypeReference<List<CommentOrderResponse>>() {});
                        log.info("Successfully parsed {} comments from job params.", comments.size());
                    }

                } catch (Exception e) {
                    log.warn("Could not parse params JSON for job ID {}: {}", job.getJobId(), e.getMessage());
                }
            }

            // 1. Cập nhật trạng thái Job -> RUNNING
            job.setStatus(JobStatus.RUNNING);
            job.setStartedAt(LocalDateTime.now(ZoneOffset.UTC));
            job.setProgressPct(10);
            reportJobRepository.save(job);

            // 2. Lấy dữ liệu TestOrder
            TestOrder testOrder = testOrderRepository.findById(job.getOrderId())
                    .orElseThrow(() -> new RuntimeException("TestOrder " + job.getOrderId() + " not found for job " + jobId));

            if (testOrder.isDeleted() || testOrder.getStatus() != OrderStatus.COMPLETED) {
                throw new IllegalStateException("Test Order " + job.getOrderId() + " is no longer valid for printing.");
            }

            List<TestResult> results = testOrder.getResults();

            // --- SỬA 4: Xóa List<OrderComment> comments = new ArrayList<>(); ---
            // (Đã chuyển lên trên)

            job.setProgressPct(30);
            reportJobRepository.save(job);

            // 3. Tạo file PDF
            // --- SỬA 5: Truyền DTO comments (List<CommentOrderResponse>) ---
            // LƯU Ý: Bạn cũng phải cập nhật phương thức `generateTestResultPdf`
            // trong `PdfGeneratorUtil.java` để nó chấp nhận `List<CommentOrderResponse>`
            byte[] pdfBytes = pdfGeneratorUtil.generateTestResultPdf(testOrder, results, comments);

            if (pdfBytes == null || pdfBytes.length == 0) {
                throw new RuntimeException("Generated PDF is empty or null.");
            }
            log.info("Generated pdfBytes length: {} bytes", pdfBytes.length);
            job.setProgressPct(70);
            reportJobRepository.save(job);

            // 4. Xác định tên file
            String fileName = determineFileName(testOrder, customFileName);

            // 5. Lưu trữ file PDF
            String fileKey = fileStorageService.storeFile(pdfBytes, customSavePath, fileName, "application/pdf", job.getRequestedBy());

            // 6. Tạo bản ghi ReportFileStore
            ReportFileStore fileStore = ReportFileStore.builder()
                    .storageType(fileStorageService.getStorageType())
                    .objectKey(fileKey)
                    .fileName(fileName)
                    .mimeType("application/pdf")
                    .byteSize((long) pdfBytes.length)
                    .createdBy(job.getRequestedBy())
                    .build();
            ReportFileStore savedFileStore = fileStoreRepository.save(fileStore);

            // 7. Cập nhật Job -> SUCCEEDED
            job.setResultFileId(savedFileStore.getFileId());
            job.setResultFile(savedFileStore);
            job.setStatus(JobStatus.SUCCEEDED);
            job.setMessage("PDF generated successfully: " + fileKey);
            job.setProgressPct(100);
            job.setFinishedAt(LocalDateTime.now(ZoneOffset.UTC));
            reportJobRepository.save(job);

            log.info("Successfully generated PDF for job ID: {}. File ID: {}. Location: {}", jobId, savedFileStore.getFileId(), fileKey);

        } catch (Exception e) {
            log.error("Failed to generate PDF for job ID: {}", jobId, e);
            job.setStatus(JobStatus.FAILED);
            job.setMessage("Error during PDF generation: " + e.getMessage());
            job.setFinishedAt(LocalDateTime.now(ZoneOffset.UTC));
            job.setProgressPct(job.getProgressPct());
            reportJobRepository.save(job);
        }
    }

    // ... (Các hàm determineFileName và removeAccents giữ nguyên) ...
    private String determineFileName(TestOrder order, String customFileName) {

        String finalBaseName;

        if (customFileName != null && !customFileName.isBlank()) {
            finalBaseName = removeAccents(customFileName);
        } else {
            String patientName = order.getFullName();
            String safePatientName = removeAccents(patientName);
            String datePrint = LocalDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ofPattern("yyyyMMdd"));
            finalBaseName = String.format("KetQuaXetNghiem-%s-%s", safePatientName, datePrint);
        }

        String sanitizedName = finalBaseName
                .replaceAll("[^a-zA-Z0-9\\s\\-_.]+", "_")
                .replace(" ", "_");

        return sanitizedName.toLowerCase().endsWith(".pdf") ? sanitizedName : sanitizedName + ".pdf";
    }

    private static String removeAccents(String text) {
        if (text == null || text.isBlank()) {
            return text;
        }
        String normalized = Normalizer.normalize(text, Normalizer.Form.NFD);
        Pattern pattern = Pattern.compile("\\p{InCombiningDiacriticalMarks}+");
        String noAccents = pattern.matcher(normalized).replaceAll("");
        noAccents = noAccents.replace('đ', 'd').replace('Đ', 'D');
        return noAccents;
    }
}