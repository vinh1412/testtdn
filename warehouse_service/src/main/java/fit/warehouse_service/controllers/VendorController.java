package fit.warehouse_service.controllers;

import fit.warehouse_service.dtos.response.ApiResponse;
import fit.warehouse_service.dtos.response.VendorResponse;
import fit.warehouse_service.services.VendorService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/v1/warehouse/vendors")
@RequiredArgsConstructor
public class VendorController {

    private final VendorService vendorService;

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER', 'LAB_USER')")
    public ResponseEntity<ApiResponse<VendorResponse>> getVendorById(@PathVariable String id) {

        VendorResponse responseDto = vendorService.getVendorById(id);

        ApiResponse<VendorResponse> apiResponse = ApiResponse.<VendorResponse>builder()
                .success(true)
                .message("Vendor details retrieved successfully.")
                .data(responseDto)
                .timestamp(LocalDateTime.now())
                .build();

        return ResponseEntity.ok(apiResponse);
    }
}