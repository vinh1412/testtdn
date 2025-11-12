/*
 * @ {#} BarcodeValidationServiceImpl.java   1.0     12/11/2025
 *
 * Copyright (c) 2025 IUH. All rights reserved.
 */

package fit.instrument_service.services.impl;

import fit.instrument_service.services.BarcodeValidationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * @description: Implementation of BarcodeValidationService
 * @author: GitHub Copilot
 * @date:   12/11/2025
 * @version:    1.0
 */
@Service
@Slf4j
public class BarcodeValidationServiceImpl implements BarcodeValidationService {
    
    @Override
    public boolean isValidBarcode(String barcode) {
        // Basic validation: non-empty, alphanumeric, length between 8-20 characters
        if (!StringUtils.hasText(barcode)) {
            log.debug("Barcode validation failed: empty or null");
            return false;
        }
        
        String trimmedBarcode = barcode.trim();
        
        // Check length
        if (trimmedBarcode.length() < 8 || trimmedBarcode.length() > 20) {
            log.debug("Barcode validation failed: invalid length {}", trimmedBarcode.length());
            return false;
        }
        
        // Check if alphanumeric with hyphens/underscores allowed
        if (!trimmedBarcode.matches("^[A-Za-z0-9_-]+$")) {
            log.debug("Barcode validation failed: contains invalid characters");
            return false;
        }
        
        log.debug("Barcode validation passed for: {}", trimmedBarcode);
        return true;
    }
}
