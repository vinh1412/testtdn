/*
 * @ {#} ReagentCheckService.java   1.0     12/11/2025
 *
 * Copyright (c) 2025 IUH. All rights reserved.
 */

package fit.instrument_service.services;

/**
 * @description: Service interface for checking reagent levels
 * @author: GitHub Copilot
 * @date:   12/11/2025
 * @version:    1.0
 */
public interface ReagentCheckService {
    
    /**
     * Check if reagent levels are sufficient for the instrument
     *
     * @param instrumentId The instrument ID
     * @return true if sufficient, false otherwise
     */
    boolean areReagentsSufficient(String instrumentId);
}
