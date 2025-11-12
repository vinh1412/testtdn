/*
 * @ {#} BarcodeValidationService.java   1.0     12/11/2025
 *
 * Copyright (c) 2025 IUH. All rights reserved.
 */

package fit.instrument_service.services;

/**
 * @description: Service interface for validating barcodes
 * @author: GitHub Copilot
 * @date:   12/11/2025
 * @version:    1.0
 */
public interface BarcodeValidationService {
    
    /**
     * Validate if a barcode is valid
     *
     * @param barcode The barcode to validate
     * @return true if valid, false otherwise
     */
    boolean isValidBarcode(String barcode);
}
