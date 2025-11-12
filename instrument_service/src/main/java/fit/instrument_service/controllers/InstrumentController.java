/*
 * @ {#} InstrumentController.java   1.0     12/11/2025
 *
 * Copyright (c) 2025 IUH. All rights reserved.
 */

package fit.instrument_service.controllers;

import fit.instrument_service.dtos.request.ChangeInstrumentModeRequest;
import fit.instrument_service.dtos.response.ApiResponse;
import fit.instrument_service.dtos.response.InstrumentResponse;
import fit.instrument_service.services.InstrumentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/*
 * @description: Controller for managing Instruments.
 * @author: Tran Hien Vinh
 * @date:   12/11/2025
 * @version:    1.0
 */
@RestController
@RequestMapping("/api/v1/instruments")
@RequiredArgsConstructor
public class InstrumentController {
    private final InstrumentService instrumentService;

    @PatchMapping("/{instrumentId}/mode")
    @PreAuthorize("hasRole('ADMIN') or hasRole('TECHNICIAN')")
    public ResponseEntity<ApiResponse<InstrumentResponse>> changeInstrumentMode(
            @PathVariable String instrumentId,
            @Valid @RequestBody ChangeInstrumentModeRequest request) {

        InstrumentResponse updatedInstrument = instrumentService.changeInstrumentMode(instrumentId, request);

        return ResponseEntity.ok(ApiResponse.success(updatedInstrument,
                "Instrument mode updated successfully"));
    }

}
