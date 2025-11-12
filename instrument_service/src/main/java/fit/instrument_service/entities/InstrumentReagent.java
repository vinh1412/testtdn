/*
 * @ (#) InstrumentReagent.java    1.0    12/11/2025
 * Copyright (c) 2025 IUH. All rights reserved.
 */
package fit.instrument_service.entities;/*
 * @description:
 * @author: Bao Thong
 * @date: 12/11/2025
 * @version: 1.0
 */

import fit.instrument_service.embedded.Vendor;
import fit.instrument_service.enums.ReagentStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.LocalDate;

@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "instrumentReagents")
// Quản lý các lô hóa chất đang được cài đặt trên thiết bị
public class InstrumentReagent extends BaseDocument {

    @Id
    private String id;

    @Field("instrument_id")
    private String instrumentId; // 'id' của Instrument

    @Field("reagent_name")
    private String reagentName; // Tên hóa chất (Req 3.6.2.1)

    @Field("lot_number")
    private String lotNumber; // Số lô

    @Field("quantity")
    private Integer quantity; // Số lượng (Req 3.6.2.1)

    @Field("expiration_date")
    private LocalDate expirationDate; // Ngày hết hạn (Req 3.6.2.1)

    @Field("status")
    private ReagentStatus status; // Trạng thái: "In Use", "Not In Use" (Req 3.6.2.2)

    @Field("vendor")
    private Vendor vendor; // Thông tin nhà cung cấp (Req 3.6.2.1)

    // Ghi chú: 'isDeleted' trong BaseDocument dùng cho Req 3.6.2.3
}
