/*
 * @ (#) CreateInstrumentRequest.java    1.0    29/10/2025
 * Copyright (c) 2025 IUH. All rights reserved.
 */
package fit.warehouse_service.dtos.request;/*
 * @description:
 * @author: Bao Thong
 * @date: 29/10/2025
 * @version: 1.0
 */

import fit.warehouse_service.enums.ProtocolType;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;

import java.util.Set;

@Getter
@Setter
public class CreateInstrumentRequest {
    @NotEmpty(message = "Instrument name cannot be empty.")
    private String name;

    @NotEmpty(message = "IP address cannot be empty.")
    private String ipAddress;

    @NotNull(message = "Port cannot be null.")
    @Positive(message = "Port must be a positive number.")
    private Integer port;

    private ProtocolType protocolType;

    private Set<String> compatibleReagentIds;
    private Set<String> configurationSettingIds;
    private String cloneFromInstrumentId;

    @NotEmpty(message = "Model cannot be empty.")
    private String model;

    @NotEmpty(message = "Type cannot be empty.")
    private String type;

    @NotEmpty(message = "Serial number cannot be empty.")
    private String serialNumber;

    @NotEmpty(message = "Vendor ID cannot be empty.")
    private String vendorId;
}