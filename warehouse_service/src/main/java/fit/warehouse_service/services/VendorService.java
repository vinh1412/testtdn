package fit.warehouse_service.services;

import fit.warehouse_service.dtos.response.VendorResponse;

public interface VendorService {
    VendorResponse getVendorById(String vendorId);
}