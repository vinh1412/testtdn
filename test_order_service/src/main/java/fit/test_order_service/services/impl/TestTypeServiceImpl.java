package fit.test_order_service.services.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import feign.FeignException;
import fit.test_order_service.client.WarehouseFeignClient;
import fit.test_order_service.client.dtos.TestParameterResponse;
import fit.test_order_service.dtos.request.CreateTestTypeRequest;
import fit.test_order_service.dtos.response.ApiResponse;
import fit.test_order_service.dtos.response.TestTypeResponse;
import fit.test_order_service.entities.TestType;
import fit.test_order_service.exceptions.BadRequestException;
import fit.test_order_service.repositories.TestTypeRepository;
import fit.test_order_service.services.TestTypeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class TestTypeServiceImpl implements TestTypeService {

    private final TestTypeRepository testTypeRepository;
    private final WarehouseFeignClient warehouseFeignClient;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public TestTypeResponse createTestType(CreateTestTypeRequest request) {
        // 1. Validate trùng tên
        if (testTypeRepository.existsByName(request.getName())) {
            throw new BadRequestException("TestType with name '" + request.getName() + "' already exists.");
        }

        // 2. Validate Test Parameters từ Warehouse
        // (Nếu user có gửi list params)
        if (request.getTestParameterIds() != null && !request.getTestParameterIds().isEmpty()) {
            try {
                log.info("Validating test parameters with Warehouse: {}", request.getTestParameterIds());
                ApiResponse<Boolean> validResponse = warehouseFeignClient.validateTestParameters(request.getTestParameterIds());

                if (validResponse == null || !Boolean.TRUE.equals(validResponse.getData())) {
                    throw new BadRequestException("One or more TestParameter IDs are invalid or not found in Warehouse.");
                }
            } catch (Exception e) {
                log.error("Error calling Warehouse Service", e);
                throw new BadRequestException("Failed to validate test parameters with Warehouse Service: " + e.getMessage());
            }
        }

        try {
            ApiResponse<Boolean> reagentAvailable =
                    warehouseFeignClient.checkReagentAvailability(
                            request.getReagentName(),
                            request.getRequiredVolume()
                    );

            if (reagentAvailable == null || !Boolean.TRUE.equals(reagentAvailable.getData())) {
                throw new BadRequestException("Reagent '" + request.getReagentName() +
                        "' does not have enough volume in Warehouse.");
            }

        } catch (FeignException e) {

            String raw = e.contentUTF8();
            log.error("Warehouse raw body = {}", raw);

            try {
                JsonNode node = objectMapper.readTree(raw);
                log.error("Parsed Warehouse error response: {}", node);

                if (node.has("message")) {
                    // Trả message
                    throw new BadRequestException(node.get("message").asText());
                }

                throw new BadRequestException("Warehouse Service error");

            } catch (BadRequestException bre) {
                // Bắt lại BadRequestException -> Ném ra luôn
                throw bre;

            } catch (Exception parseEx) {
                throw new BadRequestException("Warehouse Service unreachable");
            }
        }

        // 3. Convert List IDs -> JSON String
        String paramsJson = "[]";
        if (request.getTestParameterIds() != null) {
            try {
                paramsJson = objectMapper.writeValueAsString(request.getTestParameterIds());
            } catch (JsonProcessingException e) {
                throw new RuntimeException("Error processing parameters JSON", e);
            }
        }

        // 4. Tạo và Lưu Entity
        TestType testType = TestType.builder()
                .name(request.getName())
                .description(request.getDescription())
                .testParametersJson(paramsJson)
                .reagentName(request.getReagentName())
                .requiredVolume(request.getRequiredVolume()) // Giờ đã là double, không bị lỗi
                .build();

        TestType savedType = testTypeRepository.save(testType);

        // Parse test parameter IDs từ JSON
        List<String> paramIds = new ArrayList<>();
        try {
            if (savedType.getTestParametersJson() != null) {
                paramIds = objectMapper.readValue(
                        savedType.getTestParametersJson(),
                        new TypeReference<List<String>>() {
                        }
                );
            }
        } catch (Exception e) {
            log.error("Error parsing testParametersJson", e);
        }

        // === GỌI WAREHOUSE SERVICE ĐỂ LẤY DANH SÁCH PARAM ===
        List<TestParameterResponse> paramResponses = new ArrayList<>();

        for (String paramId : paramIds) {
            try {
                ApiResponse<TestParameterResponse> apiRes =
                        warehouseFeignClient.getTestParameterByTestParameterId(paramId);

                if (apiRes != null && apiRes.getData() != null) {
                    paramResponses.add(apiRes.getData());
                }
            } catch (Exception e) {
                log.warn("Failed to fetch TestParameter {} from Warehouse", paramId);
            }
        }

        // 5. Map sang Response
        return toResponse(savedType, paramResponses);
    }

    private String extractJson(String body) {
        int start = body.indexOf('{');
        int end = body.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return body.substring(start, end + 1);
        }
        return null;
    }

    @Override
    public TestTypeResponse getTestTypeById(String testTypeId) {
        TestType testType = testTypeRepository.findById(testTypeId)
                .orElseThrow(() -> new BadRequestException("TestType not found with ID: " + testTypeId));

        // Parse test parameter IDs từ JSON
        List<String> paramIds = new ArrayList<>();
        try {
            if (testType.getTestParametersJson() != null) {
                paramIds = objectMapper.readValue(
                        testType.getTestParametersJson(),
                        new TypeReference<List<String>>() {
                        }
                );
            }
        } catch (Exception e) {
            log.error("Error parsing testParametersJson", e);
        }

        // === GỌI WAREHOUSE SERVICE ĐỂ LẤY DANH SÁCH PARAM ===
        List<TestParameterResponse> paramResponses = new ArrayList<>();

        for (String paramId : paramIds) {
            try {
                ApiResponse<TestParameterResponse> apiRes =
                        warehouseFeignClient.getTestParameterByTestParameterId(paramId);

                if (apiRes != null && apiRes.getData() != null) {
                    paramResponses.add(apiRes.getData());
                }
            } catch (Exception e) {
                log.warn("Failed to fetch TestParameter {} from Warehouse", paramId);
            }
        }

        // Map sang Response
        return toResponse(testType, paramResponses);
    }


    // Helper method để map entity sang response
    private TestTypeResponse toResponse(TestType entity, List<TestParameterResponse> testParameters) {
        return TestTypeResponse.builder()
                .id(entity.getId())
                .name(entity.getName())
                .description(entity.getDescription())
                .testParametersJson(entity.getTestParametersJson())
                .reagentName(entity.getReagentName())
                .requiredVolume(entity.getRequiredVolume())
                .createdAt(entity.getCreatedAt())
                .testParameters(testParameters)
                .build();
    }
}