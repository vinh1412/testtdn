/*
 * @ {#} ReagentCheckServiceImpl.java   1.0     12/11/2025
 *
 * Copyright (c) 2025 IUH. All rights reserved.
 */

package fit.instrument_service.services.impl;

import fit.instrument_service.entities.InstrumentReagent;
import fit.instrument_service.enums.ReagentStatus;
import fit.instrument_service.repositories.InstrumentReagentRepository;
import fit.instrument_service.services.ReagentCheckService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

/**
 * @description: Implementation of ReagentCheckService
 * @author: GitHub Copilot
 * @date:   12/11/2025
 * @version:    1.0
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ReagentCheckServiceImpl implements ReagentCheckService {
    
    private final InstrumentReagentRepository instrumentReagentRepository;
    
    private static final int MINIMUM_REAGENT_QUANTITY = 10;
    
    @Override
    public boolean areReagentsSufficient(String instrumentId) {
        log.info("Checking reagent levels for instrument: {}", instrumentId);
        
        // Get all reagents in use for this instrument
        List<InstrumentReagent> reagentsInUse = instrumentReagentRepository
                .findByInstrumentIdAndStatus(instrumentId, ReagentStatus.IN_USE);
        
        if (reagentsInUse.isEmpty()) {
            log.warn("No reagents in use for instrument: {}", instrumentId);
            return false;
        }
        
        LocalDate today = LocalDate.now();
        
        // Check each reagent
        for (InstrumentReagent reagent : reagentsInUse) {
            // Check quantity
            if (reagent.getQuantity() == null || reagent.getQuantity() < MINIMUM_REAGENT_QUANTITY) {
                log.warn("Insufficient quantity for reagent: {} (current: {})", 
                        reagent.getReagentName(), reagent.getQuantity());
                return false;
            }
            
            // Check expiration date
            if (reagent.getExpirationDate() == null || reagent.getExpirationDate().isBefore(today)) {
                log.warn("Expired reagent: {} (expiration date: {})", 
                        reagent.getReagentName(), reagent.getExpirationDate());
                return false;
            }
        }
        
        log.info("Reagent levels are sufficient for instrument: {}", instrumentId);
        return true;
    }
}
