package fit.warehouse_service.mappers;

import fit.warehouse_service.dtos.response.VendorResponse;
import fit.warehouse_service.entities.Vendor;
import org.springframework.stereotype.Component;

@Component
public class VendorMapper {

    public VendorResponse toResponse(Vendor vendor) {
        if (vendor == null) {
            return null;
        }
        VendorResponse dto = new VendorResponse();
        dto.setId(vendor.getId());
        dto.setName(vendor.getName());
        dto.setContactPerson(vendor.getContactPerson());
        dto.setEmail(vendor.getEmail());
        dto.setPhone(vendor.getPhone());
        dto.setAddress(vendor.getAddress());
        return dto;
    }
}