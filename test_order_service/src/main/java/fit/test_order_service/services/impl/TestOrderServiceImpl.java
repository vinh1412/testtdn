package fit.test_order_service.services.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import fit.test_order_service.client.IamFeignClient;
import fit.test_order_service.client.PatientMedicalRecordFeignClient;
import fit.test_order_service.client.dtos.PatientMedicalRecordInternalResponse;
import fit.test_order_service.client.dtos.UserInternalResponse;
import fit.test_order_service.dtos.request.*;
import fit.test_order_service.dtos.response.*;
import fit.test_order_service.entities.*;
import fit.test_order_service.enums.*;
import fit.test_order_service.exceptions.BadRequestException;
import fit.test_order_service.exceptions.NotFoundException;
import fit.test_order_service.exceptions.UnauthorizedException;
import fit.test_order_service.mappers.TestOrderMapper;
import fit.test_order_service.repositories.*;
import fit.test_order_service.services.OrderEventLogService;
import fit.test_order_service.services.TestOrderService;
import fit.test_order_service.services.TestOrderStatusService;
import fit.test_order_service.specifications.TestOrderSpecification;
import fit.test_order_service.utils.SecurityUtils;
import fit.test_order_service.utils.SortFields;
import fit.test_order_service.utils.SortUtils;
import fit.test_order_service.validators.TestOrderValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import fit.test_order_service.services.PdfGenerationQueueService;
import fit.test_order_service.services.ExcelGenerationQueueService;

import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@Slf4j
@RequiredArgsConstructor
public class TestOrderServiceImpl implements TestOrderService {
    private final TestOrderRepository testOrderRepository;
    private final TestOrderMapper testOrderMapper;
    private final PatientMedicalRecordFeignClient patientMedicalRecordFeignClient;
    private final OrderEventLogService orderEventLogService;
    private final TestOrderValidator testOrderValidator;
    private final IamFeignClient iamFeignClient;
    private final TestOrderSpecification testOrderSpecification;
    private final TestOrderItemRepository testOrderItemRepository;

    private final TestCatalogRepository testCatalogRepository;
    private final ReportJobRepository reportJobRepository;
    private final PdfGenerationQueueService pdfGenerationQueueService;
    private final ExcelGenerationQueueService excelGenerationQueueService;
    private final ObjectMapper objectMapper;
    private final TestOrderStatusService testOrderStatusService;

    private final TestResultRepository testResultRepository;
    private final TestResultAdjustLogRepository testResultAdjustLogRepository;

    private final OrderCommentRepository orderCommentRepository;

    @Override
    public TestOrderResponse createTestOrder(CreateTestOrderRequest request) {
        // 1. Lấy thông tin bệnh nhân
        ApiResponse<PatientMedicalRecordInternalResponse> patientApiResponse = patientMedicalRecordFeignClient.getPatientMedicalRecordByCode(request.getMedicalRecordCode());
        PatientMedicalRecordInternalResponse patientData = patientApiResponse.getData();

        // 2. Dùng mapper để chuyển đổi
        TestOrder testOrder = testOrderMapper.toEntity(patientData);
        if (testOrder == null) {
            throw new RuntimeException("Could not map patient data to test order.");
        }

        // 3. Set các thông tin còn lại
        testOrder.setStatus(OrderStatus.PENDING);
        testOrder.setCreatedAt(LocalDateTime.now(ZoneOffset.UTC));

        // 4. Lấy User ID từ SecurityUtils
        String currentUserId = SecurityUtils.getCurrentUserId();
        if (currentUserId == null) {
            // Xử lý trường hợp không tìm thấy user, ví dụ: throw exception hoặc dùng giá trị mặc định
            throw new IllegalStateException("Cannot create test order without a logged-in user.");
        }
        testOrder.setCreatedBy(currentUserId);

        // 5. Lưu và trả về
        TestOrder savedTestOrder = testOrderRepository.save(testOrder);

        // 5. Ghi log sự kiện CREATED
        orderEventLogService.logEvent(savedTestOrder, EventType.CREATE, "Test order created for medical record: " + savedTestOrder.getMedicalRecordCode());
        return testOrderMapper.toResponse(savedTestOrder);
    }

    @Override
    public TestOrderDetailResponse getTestOrderById(String id) {
        if ("SYSTEM_ORDER_ID".equals(id)) {
            throw new NotFoundException("Invalid request: SYSTEM_ORDER is internal only");
        }

        TestOrder testOrder = testOrderRepository.findByOrderIdAndDeletedFalse(id)
                .orElseThrow(() -> new NotFoundException("Test order not found with id: " + id)); // Sửa lỗi cú pháp ở đây

        // Ghi log sự kiện VIEWED
        orderEventLogService.logEvent(testOrder, EventType.VIEW, "Viewed details for test order ID: " + id);

        // 1. Map thông tin cơ bản của Order
        TestOrderDetailResponse response = testOrderMapper.toDetailResponse(testOrder);

        if (testOrder.getStatus() == OrderStatus.COMPLETED) {
            response.setResults(testOrderMapper.toResultResponseList(testOrder.getResults()));
        }

        // --- BẮT ĐẦU LOGIC LẤY COMMENT (MAP THỦ CÔNG) ---

        // 2. Lấy ID của tất cả TestResult thuộc TestOrder này
        List<String> resultIds = testOrder.getResults()
                .stream()
                .map(TestResult::getResultId)
                .collect(Collectors.toList());

        // 3. Lấy top-level comments cho ORDER
        List<OrderComment> orderComments = orderCommentRepository
                .findByTargetTypeAndTargetIdAndParentIdIsNullOrderByCreatedAtAsc(
                        CommentTargetType.ORDER,
                        testOrder.getOrderId()
                );

        // 4. Lấy top-level comments cho RESULTS
        List<OrderComment> resultComments = new ArrayList<>();
        if (!resultIds.isEmpty()) {
            resultComments = orderCommentRepository
                    .findByTargetTypeAndTargetIdInAndParentIdIsNullOrderByCreatedAtAsc(
                            CommentTargetType.RESULT,
                            resultIds
                    );
        }

        // 5. Gộp, sắp xếp, và map thủ công sang DTO mới của bạn
        List<CommentOrderResponse> combinedComments = Stream.concat(
                        orderComments.stream(),
                        resultComments.stream()
                )
                .sorted(Comparator.comparing(OrderComment::getCreatedAt))
                .map(this::mapToCommentOrderResponse) // <-- Gọi hàm map thủ công
                .collect(Collectors.toList());

        // 6. Set vào response
        response.setComments(combinedComments);

        // --- KẾT THÚC LOGIC LẤY COMMENT ---

        return response;
    }

    // --- CÁC HÀM HELPER MAP THỦ CÔNG CHO getTestOrderById ---

    /**
     * Map thủ công từ Entity (OrderComment) sang DTO mới (CommentOrderResponse)
     */
    private CommentOrderResponse mapToCommentOrderResponse(OrderComment comment) {
        if (comment == null) {
            return null;
        }

        // Map các replies trước (đệ quy)
        List<CommentOrderResponse> replyDTOs = mapReplies(comment.getReplies());

        return CommentOrderResponse.builder()
                .id(comment.getCommentId())
                .author(mapAuthor(comment.getAuthorUserId())) // Gọi helper map author
                .targetInfo(mapTarget(comment.getTargetType(), comment.getTargetId())) // Gọi helper map target
                .content(comment.getContent())
                .createdAt(comment.getCreatedAt())
                .edited(comment.isEdited())
                .updatedAt(comment.getUpdatedAt())
                .replies(replyDTOs)
                .build();
    }

    /**
     * Map thủ công danh sách replies (Đệ quy)
     */
    private List<CommentOrderResponse> mapReplies(List<OrderComment> replies) {
        if (replies == null || replies.isEmpty()) {
            return Collections.emptyList();
        }
        return replies.stream()
                .map(this::mapToCommentOrderResponse) // Gọi lại hàm map chính
                .collect(Collectors.toList());
    }

    /**
     * Map thủ công thông tin Author (Sử dụng CommentAuthorResponse DTO)
     */
    private CommentAuthorResponse mapAuthor(String authorUserId) {
        if (authorUserId == null) {
            return new CommentAuthorResponse("SYSTEM", "Hệ thống", List.of("SYSTEM"));
        }
        try {
            ApiResponse<UserInternalResponse> apiResponse = iamFeignClient.getUserById(authorUserId);
            UserInternalResponse userData = (apiResponse != null) ? apiResponse.getData() : null;

            if (userData != null) {
                return CommentAuthorResponse.builder()
                        .id(userData.userId())
                        .fullName(userData.fullName())
                        .roles(userData.roleName() != null ? List.of(userData.roleName()) : List.of("UNKNOWN"))
                        .build();
            }
        } catch (Exception e) {
            log.error("Failed to fetch user data from IAM for userId: {}. Error: {}", authorUserId, e.getMessage());
        }
        return CommentAuthorResponse.builder()
                .id(authorUserId)
                .fullName("Người dùng không xác định")
                .roles(List.of("UNKNOWN"))
                .build();
    }

    /**
     * Map thủ công thông tin Target (Sử dụng CommentResponse DTO)
     */
    private CommentResponse mapTarget(CommentTargetType type, String targetId) {
        CommentResponse.CommentResponseBuilder builder = CommentResponse.builder()
                .targetType(type);

        if (type == CommentTargetType.ORDER) {
            builder.testOrderId(targetId);

        } else if (type == CommentTargetType.RESULT) {
            builder.resultId(targetId);
            try {
                Optional<TestResult> resultOpt = testResultRepository.findById(targetId);
                if (resultOpt.isPresent()) {
                    TestResult result = resultOpt.get();

                    builder.testOrderId(result.getOrderId());
                    builder.analyteName(result.getAnalyteName());
                    builder.resultValue(result.getValueText());

                    if (result.getItemRef() != null) {
                        builder.testName(result.getItemRef().getTestName());
                    } else {
                        log.warn("TestResult {} has null itemRef. (ItemId: {})", targetId, result.getItemId());
                        builder.testName("N/A (no item ref)");
                    }
                }
            } catch (Exception e) {
                log.error("Failed to fetch target info for resultId: {}. Error: {}", targetId, e.getMessage());
            }
        }
        return builder.build();
    }

    @Override
    @Transactional
    public void deleteTestOrder(String id) {
        // 1. Tìm TestOrder, đảm bảo nó tồn tại và chưa bị xóa
        TestOrder testOrder = testOrderRepository.findByOrderIdAndDeletedFalse(id)
                .orElseThrow(() -> new NotFoundException("Test order not found with id: " + id + " or it has already been deleted."));

        // Kiểm tra trạng thái
        if (testOrder.getStatus().equals(OrderStatus.COMPLETED)) {
            throw new IllegalStateException("Cannot delete test order when status is COMPLETED");
        }

        // 2. Đánh dấu là đã xóa (soft-delete)
        testOrder.setDeleted(true);
        testOrder.setDeletedBy(SecurityUtils.getCurrentUserId());
        testOrder.setDeletedAt(LocalDateTime.now(ZoneOffset.UTC));

        // 3. Soft-delete tất cả các TestOrderItem liên quan
        List<TestOrderItem> items = testOrder.getItems();
        if (items != null && !items.isEmpty()) {
            for (TestOrderItem item : items) {
                item.setDeleted(true);
                item.setDeletedBy(SecurityUtils.getCurrentUserId());
                item.setDeletedAt(LocalDateTime.now(ZoneOffset.UTC));
            }
            testOrderItemRepository.saveAll(items);
        }

        // 4. Lưu lại thay đổi của TestOrder
        testOrderRepository.save(testOrder);

        // 5. Ghi lại sự kiện DELETED vào log
        orderEventLogService.logEvent(testOrder, EventType.DELETE, "Test order with ID: " + id + " has been deleted.");
    }

    @Override
    @Transactional
    public TestOrderResponse updateTestOrderByCode(String orderCode, UpdateTestOrderRequest request) {
        // Validate and fetch existing order
        TestOrder existingOrder = testOrderValidator.validateForUpdate(orderCode);

        // Create a copy of the existing order for comparison
        TestOrder beforeUpdate = new TestOrder();
        BeanUtils.copyProperties(existingOrder, beforeUpdate);

        // Get current authenticated user
        String currentUserId = SecurityUtils.getCurrentUserId();

        // Set createdBy field
        ApiResponse<UserInternalResponse> response = iamFeignClient.getUserById(currentUserId);
        UserInternalResponse creator = response.getData();

        // Update fields if provided and changed
        if (request.getStatus() != null && !request.getStatus().isBlank()) {
            OrderStatus newStatus = OrderStatus.valueOf(request.getStatus());
            OrderStatus currentStatus = existingOrder.getStatus();

            if (!Objects.equals(currentStatus, newStatus)) {
                if (!currentStatus.canTransitionTo(newStatus)) {
                    throw new BadRequestException(String.format(
                            "Cannot change status from %s to %s", currentStatus, newStatus
                    ));
                }
                existingOrder.setStatus(newStatus);
            }
        }

        if (request.getReviewStatus() != null && !request.getReviewStatus().isBlank()) {
            ReviewStatus newReviewStatus = ReviewStatus.valueOf(request.getReviewStatus());

            if (!Objects.equals(existingOrder.getReviewStatus(), newReviewStatus)) {
                existingOrder.setReviewStatus(newReviewStatus);
            }
        }

        if (request.getReviewMode() != null && !request.getReviewMode().isBlank()) {
            ReviewMode newMode = ReviewMode.valueOf(request.getReviewMode());

            if (!Objects.equals(existingOrder.getReviewMode(), newMode)) {
                existingOrder.setReviewMode(newMode);
            }
        }

        existingOrder.setUpdatedBy(creator.fullName());

        TestOrder updatedOrder = testOrderRepository.save(existingOrder);

        // Log the update event with before and after states
        orderEventLogService.logOrderUpdate(beforeUpdate, updatedOrder, EventType.UPDATE);

        return testOrderMapper.toResponse(updatedOrder);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<TestOrderResponse> getAllTestOrders(int page, int size, String[] sort, String search,
                                                            LocalDate startDate, LocalDate endDate, OrderStatus status, ReviewStatus reviewStatus,
                                                            ReviewMode reviewMode, Gender gender, String createdBy, String reviewedBy) {
        Sort validSort = SortUtils.buildSort(
                sort,
                SortFields.TEST_ORDER_SORT_FIELDS,
                SortFields.DEFAULT_TEST_ORDER_SORT
        );

        Pageable pageable = PageRequest.of(page, size, validSort);

        Specification<TestOrder> spec = testOrderSpecification.build(search, startDate, endDate, status, reviewStatus, reviewMode, gender, createdBy, reviewedBy);

        Page<TestOrder> testOrderPage = testOrderRepository.findAll(spec, pageable);

        FilterInfo filterInfo = FilterInfo.builder()
                .search(search)
                .startDate(startDate)
                .endDate(endDate)
                .status(status)
                .reviewStatus(reviewStatus)
                .reviewMode(reviewMode)
                .gender(gender)
                .createdBy(createdBy)
                .reviewedBy(reviewedBy)
                .build();

        if (testOrderPage.isEmpty()) {
            return PageResponse.empty(page, size, "No data", filterInfo);
        }

        Page<TestOrderResponse> dtoPage = testOrderPage.map(testOrderMapper::toResponse);

        try {
            String details = String.format(
                    "User fetched test orders list [page=%d, size=%d, sort=%s, filters=%s]",
                    page, size, Arrays.toString(sort), new ObjectMapper().writeValueAsString(filterInfo)
            );
            orderEventLogService.logEvent(null, EventType.VIEW_ALL, details);
        } catch (JsonProcessingException e) {
            orderEventLogService.logEvent(null, EventType.VIEW_ALL, "User fetched test orders list (unable to serialize filters)");
        }

        return PageResponse.from(dtoPage, filterInfo);
    }

    @Override
    @Transactional
    public TestOrderItemResponse addTestOrderItem(String orderId, AddTestOrderItemRequest request) {
        TestOrder testOrder = testOrderRepository.findByOrderIdAndDeletedFalse(orderId)
                .orElseThrow(() -> new NotFoundException("Test order not found with id: " + orderId));

        // Check for duplicate testName in the same order
        boolean isDuplicate = testOrder.getItems().stream()
                .anyMatch(item -> !item.isDeleted() &&
                        item.getTestName().equalsIgnoreCase(request.getTestName()));

        if (isDuplicate) {
            throw new BadRequestException("Test already exists in this order: " + request.getTestName());
        }


        // Validate that the test name exists in the catalog
        TestCatalog catalog = testCatalogRepository.findByTestNameIgnoreCaseAndActiveTrue(request.getTestName().trim())
                .orElseThrow(() -> new BadRequestException("Test '" + request.getTestName() + "' is not in the catalog. Please add it first."));

        TestOrderItem testOrderItem = testOrderMapper.toOrderItemEntity(request);
        testOrderItem.setTestCode(catalog.getLocalCode());
        testOrderItem.setOrderRef(testOrder);

        String currentUserId = SecurityUtils.getCurrentUserId();
        if (currentUserId == null) {
            throw new IllegalStateException("Cannot create test order item without a logged-in user.");
        }
        // Gán CreatedBy cho đối tượng testOrderItem
        testOrderItem.setCreatedBy(currentUserId);

        TestOrderItem savedItem = testOrderItemRepository.save(testOrderItem);

        // Kiểm tra và cập nhật Order status khi có item mới
        testOrderStatusService.handleNewItemAdded(orderId);

        String logDetails = "Added new test item: " + savedItem.getTestCode() + " - " + savedItem.getTestName();
        orderEventLogService.logEvent(testOrder, EventType.ADD_ORDER_ITEM, logDetails);

        return testOrderMapper.toOrderItemResponse(savedItem);
    }

    @Override
    @Transactional
    public TestOrderItemResponse updateTestOrderItem(String orderId, String itemId, UpdateTestOrderItemRequest request) {
        // Validate and fetch existing order
        TestOrder testOrder = testOrderRepository.findByOrderIdAndDeletedFalse(orderId)
                .orElseThrow(() -> new NotFoundException("Test order not found with id: " + orderId));

        // Validate and fetch existing order item
        TestOrderItem existingItem = testOrderItemRepository.findByItemIdAndOrderRefOrderIdAndDeletedFalse(itemId, testOrder.getOrderId())
                .orElseThrow(() -> new NotFoundException("Test order item not found with id: " + itemId + " for order id: " + orderId));

        // Create a copy of the existing order for comparison
        TestOrderItem beforeUpdate = new TestOrderItem();
        BeanUtils.copyProperties(existingItem, beforeUpdate);

        if (request.getTestName() != null && !request.getTestName().isBlank()) {
            existingItem.setTestName(request.getTestName());
        }

        if (request.getStatus() != null && !request.getStatus().isBlank()) {
            ItemStatus newStatus = ItemStatus.valueOf(request.getStatus());
            ItemStatus currentStatus = existingItem.getStatus();

            if (!Objects.equals(existingItem.getStatus(), newStatus)) {
                if (!currentStatus.canTransitionTo(newStatus)) {
                    throw new BadRequestException(String.format(
                            "Cannot change item status from %s to %s", currentStatus, newStatus
                    ));
                }
                existingItem.setStatus(newStatus);
            }
        }

        String currentUserId = SecurityUtils.getCurrentUserId();
        if (currentUserId == null) {
            throw new IllegalStateException("Cannot create test order item without a logged-in user.");
        }
        // Update UpdatedBy for the order item
        existingItem.setUpdatedBy(currentUserId);

        TestOrderItem afterUpdate = testOrderItemRepository.save(existingItem);

        // Cập nhật Order status nếu item status thay đổi
        testOrderStatusService.updateOrderStatusIfNeeded(orderId);

        orderEventLogService.logTestOrderItemUpdate(beforeUpdate, afterUpdate, EventType.UPDATE_ORDER_ITEM);

        return testOrderMapper.toOrderItemResponse(afterUpdate);
    }

    @Override
    public TestOrderItemResponse getTestOrderItemById(String orderId, String itemId) {
        // 1. Kiểm tra sự tồn tại của TestOrder
        if (!testOrderRepository.existsByOrderId(orderId)) {
            throw new NotFoundException("Test order not found with id: " + orderId);
        }


        // 2. Tìm TestOrderItem
        TestOrderItem testOrderItem = testOrderItemRepository.findByOrderRefOrderIdAndItemIdAndDeletedFalse(orderId, itemId)
                .orElseThrow(() -> new NotFoundException("Test order item not found with id: " + itemId + " in order: " + orderId));

        // 3. Ghi log sự kiện
        orderEventLogService.logEvent(testOrderItem.getOrderRef(), EventType.VIEW_ORDER_ITEM, "Viewed details for test order item ID: " + itemId);

        // 4. Map sang DTO và trả về
        return testOrderMapper.toOrderItemResponse(testOrderItem);
    }

    @Override
    @Transactional
    public void deleteTestOrderItem(String orderId, String itemId) {
        TestOrder testOrder = testOrderRepository.findByOrderIdAndDeletedFalse(orderId)
                .orElseThrow(() -> new NotFoundException("Test order not found with id: " + orderId));

        if (testOrder.getStatus() == OrderStatus.COMPLETED) {
            throw new BadRequestException("Cannot delete item from a completed test order.");
        }

        TestOrderItem testOrderItem = testOrderItemRepository.findByItemIdAndDeletedFalse(itemId)
                .orElseThrow(() -> new NotFoundException("Test order item not found with id: " + itemId));

        if (!testOrderItem.getOrderRef().getOrderId().equals(orderId)) {
            throw new NotFoundException("Test order item with id " + itemId + " does not belong to order with id " + orderId);
        }

        testOrderItem.setDeleted(true);
        testOrderItem.setDeletedBy(SecurityUtils.getCurrentUserId());
        testOrderItem.setDeletedAt(LocalDateTime.now(ZoneOffset.UTC));

        testOrderItemRepository.save(testOrderItem);

        // Cập nhật Order status sau khi xóa item
        testOrderStatusService.updateOrderStatusIfNeeded(orderId);

        String logDetails = "Soft deleted test item: " + testOrderItem.getTestCode() + " - " + testOrderItem.getTestName();
        orderEventLogService.logEvent(testOrder, EventType.DELETE_ORDER_ITEM, logDetails);
    }

    @Override
    @Transactional
    public PrintJobResponse requestPrintOrder(String orderId, PrintTestOrderRequest request) {
        if ("SYSTEM_ORDER_ID".equals(orderId)) {
            throw new BadRequestException("Cannot print the system order.");
        }
        // 1. Kiểm tra Test Order
        TestOrder testOrder = testOrderRepository.findByOrderIdAndDeletedFalse(orderId)
                .orElseThrow(() -> new NotFoundException("Test order not found with id: " + orderId));

        if (testOrder.isDeleted()) {
            throw new NotFoundException("Test order with id: " + orderId + " has been deleted.");
        }
        if (testOrder.getStatus() != OrderStatus.COMPLETED) {
            throw new BadRequestException("Test order must have status 'Completed' to be printed. Current status: " + testOrder.getStatus());
        }

        // 2. Lấy User ID người yêu cầu
        String currentUserId = SecurityUtils.getCurrentUserId();
        if (currentUserId == null) {
            throw new UnauthorizedException("Cannot request print job without a logged-in user.");
        }

        // 3. Xử lý các tùy chọn in (tên file, đường dẫn)
        String requestedFileName = null;
        String requestedSavePath = null;

        if (request != null) {
            if (request.getCustomFileName() != null && !request.getCustomFileName().isBlank()) {
                requestedFileName = request.getCustomFileName().trim();
            }
            if (request.getCustomSavePath() != null && !request.getCustomSavePath().isBlank()) {
                requestedSavePath = request.getCustomSavePath().trim();
            }
        }

        // 3b. Lấy comments
        log.info("Including comments for print job for order: {}", orderId);
        List<String> resultIds = testOrder.getResults()
                .stream()
                .map(TestResult::getResultId)
                .collect(Collectors.toList());
        List<OrderComment> orderComments = orderCommentRepository
                .findByTargetTypeAndTargetIdAndParentIdIsNullOrderByCreatedAtAsc(
                        CommentTargetType.ORDER,
                        testOrder.getOrderId()
                );
        List<OrderComment> resultComments = new ArrayList<>();
        if (!resultIds.isEmpty()) {
            resultComments = orderCommentRepository
                    .findByTargetTypeAndTargetIdInAndParentIdIsNullOrderByCreatedAtAsc(
                            CommentTargetType.RESULT,
                            resultIds
                    );
        }
        List<CommentOrderResponse> commentsToPrint = Stream.concat(orderComments.stream(), resultComments.stream())
                .sorted(Comparator.comparing(OrderComment::getCreatedAt))
                .map(this::mapToCommentOrderResponse) // Dùng lại helper map thủ công
                .collect(Collectors.toList());

        // 4. Tạo đối tượng ReportJob
        ReportJob printJob = ReportJob.builder()
                .jobType(JobType.PRINT_ORDER_PDF)
                .status(JobStatus.QUEUED)
                .requestedBy(currentUserId)
                .orderId(orderId)
                .printOrderRef(testOrder)
                // Truyền comments vào (có thể là list rỗng, không sao)
                .paramsJson(createParamsJson(requestedFileName, requestedSavePath, commentsToPrint))
                .build();

        ReportJob savedJob = reportJobRepository.save(printJob);

        // 5. Đưa Job ID vào hàng đợi xử lý ngầm
        pdfGenerationQueueService.queuePdfGeneration(savedJob.getJobId());

        // 6. Ghi log sự kiện
        String logDetails = "Print job requested with Job ID: " + savedJob.getJobId() + " (Comments included)";
        orderEventLogService.logEvent(testOrder, EventType.PRINT_REQUEST, logDetails);

        // 7. Trả về thông tin job cho client
        return PrintJobResponse.builder()
                .jobId(savedJob.getJobId())
                .orderId(orderId)
                .status(savedJob.getStatus())
                .message("Print job successfully queued.")
                .requestedAt(savedJob.getCreatedAt())
                .build();
    }

    // --- CẬP NHẬT PHƯƠNG THỨC NÀY ---
    // Helper để tạo JSON lưu trữ tham số
    private String createParamsJson(String customFileName, String customSavePath, List<CommentOrderResponse> comments) {
        Map<String, Object> params = new HashMap<>();

        if (customFileName != null) {
            params.put("customFileName", customFileName);
        }
        if (customSavePath != null) {
            params.put("customSavePath", customSavePath);
        }
        // Luôn thêm comments (nếu nó không rỗng)
        if (comments != null && !comments.isEmpty()) {
            params.put("comments", comments);
        }

        if (params.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(params);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize print job params to JSON", e);
            return null;
        }
    }

    @Override
    @Transactional
    public PrintJobResponse requestExportExcel(ExportExcelRequest request) {
        String currentUserId = SecurityUtils.getCurrentUserId();
        if (currentUserId == null) {
            throw new UnauthorizedException("Cannot request export job without a logged-in user.");
        }

        // --- Xử lý logic mới cho việc xác định phạm vi export ---
        List<String> targetOrderIds = new ArrayList<>();
        String dateRangeType = request.getDateRangeType() != null ? request.getDateRangeType() : "THIS_MONTH"; // Mặc định là THIS_MONTH
        LocalDate startDate = request.getStartDate();
        LocalDate endDate = request.getEndDate();
        boolean useDateRange = false; // Cờ xác định có dùng lọc theo ngày không

        if (request.getOrderIds() != null && !request.getOrderIds().isEmpty()) {
            // Ưu tiên export theo danh sách ID nếu có
            targetOrderIds = request.getOrderIds();
            long count = testOrderRepository.countByOrderIdInAndDeletedFalse(targetOrderIds);
            if (count != targetOrderIds.size()) {
                List<String> foundIds = testOrderRepository.findByOrderIdInAndDeletedFalse(targetOrderIds)
                        .stream().map(TestOrder::getOrderId).toList();
                List<String> missingIds = targetOrderIds.stream().filter(id -> !foundIds.contains(id)).toList();
                throw new NotFoundException("One or more Test Orders not found or deleted: " + String.join(", ", missingIds));
            }
            log.info("Exporting specific {} non-deleted orders by ID list.", targetOrderIds.size());
            // Khi export theo ID list, không dùng lọc theo ngày
            dateRangeType = null; // Reset dateRangeType để worker không query theo ngày nữa
            startDate = null;
            endDate = null;
        } else {
            // Nếu không có orderIds, sẽ export theo khoảng thời gian
            useDateRange = true;
            log.info("Exporting orders based on date range type: {}", dateRangeType);
            if ("CUSTOM".equals(dateRangeType)) {
                log.info("Custom date range: {} to {}", startDate, endDate);
                // Validation đã kiểm tra startDate/endDate không null
            }
        }

        // Lấy tên file và đường dẫn tùy chỉnh (giữ nguyên)
        String requestedFileName = null;
        String requestedSavePath = null;
        if (request.getCustomFileName() != null && !request.getCustomFileName().isBlank()) {
            requestedFileName = request.getCustomFileName().trim();
        }
        if (request.getCustomSavePath() != null && !request.getCustomSavePath().isBlank()) {
            requestedSavePath = request.getCustomSavePath().trim();
        }

        // Tạo ReportJob
        ReportJob exportJob = ReportJob.builder()
                .jobType(JobType.EXPORT_ORDERS_XLSX)
                .status(JobStatus.QUEUED)
                .requestedBy(currentUserId)
                // Lưu tham số mới vào JSON
                .paramsJson(createExportParamsJson(targetOrderIds, requestedFileName, requestedSavePath, dateRangeType, startDate, endDate))
                .build();

        ReportJob savedJob = reportJobRepository.save(exportJob);

        // Đưa vào hàng đợi Excel
        excelGenerationQueueService.queueExcelGeneration(savedJob.getJobId());
        orderEventLogService.logEvent(null, EventType.EXPORT_REQUEST, "Excel export job requested with Job ID: " + savedJob.getJobId());

        // Trả về response
        return PrintJobResponse.builder()
                .jobId(savedJob.getJobId())
                .orderId(null)
                .status(savedJob.getStatus())
                .message("Export Excel job successfully queued.")
                .requestedAt(savedJob.getCreatedAt())
                .build();
    }

    // Helper mới để tạo JSON cho export excel (cập nhật tham số)
    private String createExportParamsJson(List<String> orderIds, String customFileName, String customSavePath,
                                          String dateRangeType, LocalDate startDate, LocalDate endDate) {
        Map<String, Object> params = new HashMap<>();

        if (orderIds != null && !orderIds.isEmpty()) {
            params.put("orderIds", orderIds);
        }
        if (customFileName != null) {
            params.put("customFileName", customFileName);
        }
        if (customSavePath != null) {
            params.put("customSavePath", customSavePath);
        }
        // Thêm các tham số thời gian
        if (dateRangeType != null) { // Chỉ thêm nếu không phải export theo ID list
            params.put("dateRangeType", dateRangeType);
            if ("CUSTOM".equals(dateRangeType)) {
                // Chuyển LocalDate thành String YYYY-MM-DD để lưu vào JSON
                params.put("startDate", startDate.format(DateTimeFormatter.ISO_LOCAL_DATE));
                params.put("endDate", endDate.format(DateTimeFormatter.ISO_LOCAL_DATE));
            }
        }

        // Không cần kiểm tra empty vì nếu không có ID thì dateRangeType sẽ có giá trị
        try {
            return objectMapper.writeValueAsString(params);
        } catch (JsonProcessingException e) {
            log.error("Error creating params JSON for excel export job", e);
            return null;
        }
    }

    @Override
    @Transactional
    public ReviewTestOrderResponse reviewTestOrder(String orderId, ReviewTestOrderRequest request) {
        String currentUserId = SecurityUtils.getCurrentUserId();

        // 1. Validate Acceptance Criteria: Tồn tại, chưa xóa
        TestOrder testOrder = testOrderRepository.findByOrderIdAndDeletedFalse(orderId)
                .orElseThrow(() -> new NotFoundException("Test order not found or has been deleted: " + orderId));

        // 2. Validate Acceptance Criteria: Phải ở trạng thái COMPLETED
        if (testOrder.getStatus() != OrderStatus.COMPLETED) {
            throw new BadRequestException(
                    "Test order must be in 'COMPLETED' status to be reviewed. Current status: " + testOrder.getStatus()
            );
        }

        ReviewMode mode = request.getReviewMode();
        List<TestResultAdjustLog> logsToSave = new ArrayList<>();
        int adjustmentsCount = 0;

        // 3. Xử lý các điều chỉnh (Adjustments)
        if (request.getAdjustments() != null && !request.getAdjustments().isEmpty()) {
            for (ResultAdjustmentRequest adj : request.getAdjustments()) {

                // 3a. Tìm kết quả (TestResult)
                TestResult testResult = testResultRepository.findById(adj.getResultId())
                        .orElseThrow(() -> new NotFoundException("TestResult not found: " + adj.getResultId()));

                // 3b. Đảm bảo TestResult thuộc về TestOrder này
                if (!testResult.getOrderId().equals(orderId)) {
                    throw new BadRequestException(
                            "TestResult ID " + adj.getResultId() + " does not belong to Order ID " + orderId
                    );
                }

                String beforeValue = testResult.getValueText();
                String afterValue = adj.getNewValueText();

                // 3c. Chỉ xử lý và ghi log nếu giá trị thực sự thay đổi
                if (!Objects.equals(beforeValue, afterValue)) {

                    // Ghi log cảnh báo về việc validate "acceptable range"
                    log.warn("TODO: Validate if change from '{}' to '{}' is within acceptable range for analyte {}.",
                            beforeValue, afterValue, testResult.getAnalyteName());

                    // 3d. Cập nhật giá trị mới cho TestResult
                    testResult.setValueText(afterValue);
                    testResultRepository.save(testResult);

                    // 3e. Tạo TestResultAdjustLog
                    TestResultAdjustLog logEntry = TestResultAdjustLog.builder()
                            .orderId(orderId)
                            .resultId(adj.getResultId())
                            .reviewMode(mode)
                            .actorUserId(currentUserId)
                            .field("valueText")
                            .beforeValue(beforeValue)
                            .afterValue(afterValue)
                            .note(adj.getNote())
                            .build();

                    logsToSave.add(logEntry);
                    adjustmentsCount++;
                }
            }

            // 3f. Lưu tất cả log điều chỉnh
            if (!logsToSave.isEmpty()) {
                testResultAdjustLogRepository.saveAll(logsToSave);
            }
        }

        // 4. Cập nhật trạng thái Review của TestOrder
        testOrder.setReviewStatus(ReviewStatus.HUMAN_REVIEWED);
        testOrder.setReviewMode(mode);
        testOrder.setReviewedBy(currentUserId);
        testOrder.setReviewedAt(LocalDateTime.now(ZoneOffset.UTC));
        testOrderRepository.save(testOrder);

        // 5. Ghi log sự kiện Review chính
        EventType eventType = (mode == ReviewMode.AI) ? EventType.REVIEW_AI : EventType.REVIEW_HUMAN;
        String logDetails = String.format("Order reviewed. Mode: %s. %d adjustments logged. Note: %s",
                mode, adjustmentsCount, request.getNote());

        orderEventLogService.logEvent(testOrder, eventType, logDetails);

        // 6. Trả về Response
        return ReviewTestOrderResponse.builder()
                .orderId(orderId)
                .reviewStatus(testOrder.getReviewStatus())
                .reviewedBy(currentUserId)
                .reviewedAt(testOrder.getReviewedAt())
                .adjustmentsLogged(adjustmentsCount)
                .message("Test order reviewed successfully.")
                .build();
    }
}