package fit.instrument_service.client.dtos;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TestParameterResponse {
    private String id;
    private String code;
    private String name;
    private String unit;
    private String referenceRange;
}