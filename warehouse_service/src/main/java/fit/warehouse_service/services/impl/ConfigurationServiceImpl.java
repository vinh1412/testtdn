/*
 * @ (#) ConfigurationServiceImpl.java    1.0    03/11/2025
 * Copyright (c) 2025 IUH. All rights reserved.
 */
package fit.warehouse_service.services.impl;/*
 * @description:
 * @author: Bao Thong
 * @date: 03/11/2025
 * @version: 1.0
 */

import com.fasterxml.jackson.databind.ObjectMapper;
import fit.warehouse_service.constants.SortFields;
import fit.warehouse_service.dtos.request.CreateConfigurationRequest;
import fit.warehouse_service.dtos.request.ModifyConfigurationRequest;
import fit.warehouse_service.dtos.response.ConfigurationFilterInfo;
import fit.warehouse_service.dtos.response.ConfigurationResponse;
import fit.warehouse_service.dtos.response.PageResponse;
import fit.warehouse_service.entities.ConfigurationSetting;
import fit.warehouse_service.enums.DataType;
import fit.warehouse_service.enums.WarehouseActionType;
import fit.warehouse_service.exceptions.DuplicateResourceException;
import fit.warehouse_service.exceptions.NotFoundException;
import fit.warehouse_service.exceptions.ValidateValueFormatException;
import fit.warehouse_service.mappers.ConfigurationMapper;
import fit.warehouse_service.repositories.ConfigurationSettingRepository;
import fit.warehouse_service.services.ConfigurationService;
import fit.warehouse_service.services.WarehouseEventLogService;
import fit.warehouse_service.specifications.ConfigurationSpecification;
import fit.warehouse_service.utils.SecurityUtils;
import fit.warehouse_service.utils.SortUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Slf4j
public class ConfigurationServiceImpl implements ConfigurationService {

    private final ConfigurationSettingRepository configurationSettingRepository;
    private final WarehouseEventLogService logService;
    private final ConfigurationMapper configurationMapper;
    private final ConfigurationSpecification configurationSpecification;

    @Override
    @Transactional
    public ConfigurationResponse createConfiguration(CreateConfigurationRequest request) {
        log.info("Creating new configuration: {}", request.getName());

        // 1. Yêu cầu 3.3.3.1: "must be unique... prevent any duplication"
        configurationSettingRepository.findByName(request.getName()).ifPresent(c -> {
            throw new DuplicateResourceException("Configuration with name '" + request.getName() + "' already exists.");
        });

        // 2. Yêu cầu 3.3.3.1: "fill out the necessary fields" (đã được DTO validate)
        ConfigurationSetting newConfig = new ConfigurationSetting();
        newConfig.setName(request.getName());
        newConfig.setDescription(request.getDescription());
        newConfig.setDataType(request.getDataType());
        newConfig.setValue(request.getValue());
        // BaseEntity fields (id, createdAt, createdBy) sẽ được tự động điền

        // 3. Lưu vào DB
        ConfigurationSetting savedConfig = configurationSettingRepository.save(newConfig);

        // 4. Yêu cầu 3.3.3.1: "sync up to other services" (Sử dụng Event Log)
        String logDetails = logService.createConfigurationCreatedDetails(savedConfig);
        logService.logEvent(
                WarehouseActionType.CONFIG_CREATED,
                savedConfig.getId(),
                "ConfigurationSetting",
                logDetails
        );

        log.info("Successfully created configuration with ID: {}", savedConfig.getId());

        // 5. Yêu cầu 3.3.3.1: "confirming that the configuration has been created successfully"
        return configurationMapper.toResponse(savedConfig);
    }

    @Override
    @Transactional
    public ConfigurationResponse modifyConfiguration(String configurationId, ModifyConfigurationRequest request) {
        // Kiểm tra tồn tại của configuration
        ConfigurationSetting existingConfig = configurationSettingRepository
                .findByIdAndDeletedAtIsNull(configurationId)
                .orElseThrow(() -> new NotFoundException(
                        "Configuration with ID '" + configurationId + "' not found or has been deleted."));

        // Convert Object -> String JSON/primitive phù hợp để lưu
        String convertedValue = convertValueToString(request.getNewValue(), existingConfig.getDataType());

        // Xác thực định dạng giá trị dựa trên kiểu dữ liệu
        validateValueFormat(convertedValue, existingConfig.getDataType());

        // Lưu trữ giá trị cũ để kiểm tra thay đổi và ghi log
        String oldValue = existingConfig.getValue();

        // Kiểm tra xem giá trị có thực sự thay đổi không
        if (Objects.equals(oldValue, request.getNewValue())) {
            log.info("No modification applied for config {} — same value '{}'", existingConfig.getName(), oldValue);
            return configurationMapper.toResponse(existingConfig);
        }

        // Cập nhật giá trị mới và thông tin sửa đổi
        existingConfig.setValue(convertedValue);
        existingConfig.setUpdatedAt(LocalDateTime.now());
        existingConfig.setUpdatedByUserId(SecurityUtils.getCurrentUserId());

        ConfigurationSetting updatedConfig = configurationSettingRepository.save(existingConfig);

        // Ghi log sự kiện thay đổi cấu hình
        String logDetails = logService.createConfigurationModifiedDetails(
                updatedConfig,
                oldValue,
                convertedValue,
                request.getModificationReason()
        );

        logService.logEvent(
                WarehouseActionType.CONFIG_UPDATED,
                updatedConfig.getId(),
                "ConfigurationSetting",
                logDetails
        );

        log.info("Successfully modified configuration with ID: {}. Old value: '{}', New value: '{}'",
                updatedConfig.getId(), oldValue, request.getNewValue());

        // Trả về response sau khi sửa đổi
        return configurationMapper.toResponseUpdate(updatedConfig);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ConfigurationResponse> getAllConfigurations(int page, int size, String[] sort, String search, DataType dataType, LocalDate startDate, LocalDate endDate) {
        log.info("Retrieving all configurations with filters: search='{}', dataType='{}', startDate='{}', endDate='{}'",
                search, dataType, startDate, endDate);

        // Xây dựng đối tượng Sort hợp lệ
        Sort validSort = SortUtils.buildSort(
                sort,
                SortFields.CONFIGURATION_SORT_FIELDS,
                SortFields.DEFAULT_CONFIGURATION_SORT
        );

        Pageable pageable = PageRequest.of(page, size, validSort);

        // Xây dựng Specification dựa trên các tiêu chí lọc
        Specification<ConfigurationSetting> spec = configurationSpecification.build(search, dataType, startDate, endDate);

        // Truy vấn CSDL với phân trang và lọc
        Page<ConfigurationSetting> pageConfigs = configurationSettingRepository.findAll(spec, pageable);

        Page<ConfigurationResponse> dtoPage = pageConfigs.map(configurationMapper::toResponseUpdate);

        // Tạo đối tượng FilterInfo để trả về thông tin lọc
        ConfigurationFilterInfo filterInfo = ConfigurationFilterInfo.builder()
                .search(search)
                .dataType(dataType)
                .startDate(startDate)
                .endDate(endDate)
                .build();

        return PageResponse.from(dtoPage, filterInfo);
    }

    @Override
    @Transactional(readOnly = true)
    public ConfigurationResponse getConfigurationById(String configurationId) {
        // Tìm configuration theo ID và đảm bảo chưa bị xóa
        ConfigurationSetting configuration = configurationSettingRepository
                .findByIdAndDeletedAtIsNull(configurationId)
                .orElseThrow(() -> new NotFoundException(
                        "Configuration with ID '" + configurationId + "' not found or has been deleted."));

        // Trả về response
        return configurationMapper.toResponseUpdate(configuration);
    }

    // Phương thức để chuyển đổi Object newValue thành String phù hợp dựa trên DataType
    private String convertValueToString(Object newValue, DataType dataType) {
        if (newValue == null)
            throw new ValidateValueFormatException("New value cannot be null");

        try {
            ObjectMapper mapper = new ObjectMapper();

            return switch (dataType) {
                case INTEGER, BOOLEAN -> String.valueOf(newValue);
                case JSON -> mapper.writeValueAsString(newValue);
                case STRING -> newValue.toString();
                default -> throw new ValidateValueFormatException("Unsupported data type: " + dataType);
            };
        } catch (Exception e) {
            throw new ValidateValueFormatException("Failed to convert newValue for data type " + dataType + ": " + e.getMessage());
        }
    }

    // Phương thức để xác thực định dạng giá trị dựa trên kiểu dữ liệu
    private void validateValueFormat(String value, DataType dataType) {
        try {
            switch (dataType) {
                case INTEGER:
                    Integer.parseInt(value);
                    break;
                case BOOLEAN:
                    if (!"true".equalsIgnoreCase(value) && !"false".equalsIgnoreCase(value)) {
                        throw new ValidateValueFormatException("Data type BOOLEAN must be 'true' or 'false'.");
                    }
                    break;
                case JSON:
                    try {
                        new ObjectMapper().readTree(value); // kiểm tra parse JSON hợp lệ
                    } catch (Exception e) {
                        throw new ValidateValueFormatException("Malformed JSON: " + e.getMessage());
                    }
                    break;
                default:
                    throw new ValidateValueFormatException("Unsupported data type: " + dataType);
            }
        } catch (NumberFormatException e) {
            throw new ValidateValueFormatException("Invalid " + dataType.name().toLowerCase() + " format: " + value);
        }
    }
}
