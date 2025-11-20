package fit.warehouse_service.services.impl;

import fit.warehouse_service.dtos.response.VendorResponse;
import fit.warehouse_service.entities.Vendor;
import fit.warehouse_service.exceptions.NotFoundException;
import fit.warehouse_service.mappers.VendorMapper;
import fit.warehouse_service.repositories.VendorRepository;
import fit.warehouse_service.services.VendorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class VendorServiceImpl implements VendorService {

    private final VendorRepository vendorRepository; //
    private final VendorMapper vendorMapper;

    @Override
    public VendorResponse getVendorById(String vendorId) {
        log.info("Fetching vendor by ID: {}", vendorId);
        Vendor vendor = vendorRepository.findById(vendorId)
                .orElseThrow(() -> new NotFoundException("Vendor not found with ID: " + vendorId));

        return vendorMapper.toResponse(vendor);
    }
}