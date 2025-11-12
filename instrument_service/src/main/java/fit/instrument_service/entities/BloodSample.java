/*
 * @ (#) BloodSample.java    1.0    12/11/2025
 * Copyright (c) 2025 IUH. All rights reserved.
 */
package fit.instrument_service.entities;

import fit.instrument_service.enums.SampleStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

/**
 * @description: Blood sample entity for tracking samples in the analysis workflow
 * @author: GitHub Copilot
 * @date: 12/11/2025
 * @version: 1.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "bloodSamples")
public class BloodSample extends BaseDocument {

    @Id
    private String id;

    @Field("barcode")
    @Indexed
    private String barcode; // Sample barcode (required for processing)

    @Field("test_order_id")
    @Indexed
    private String testOrderId; // Test order ID (auto-created if missing but barcode is valid)

    @Field("workflow_id")
    @Indexed
    private String workflowId; // Reference to the workflow processing this sample

    @Field("instrument_id")
    @Indexed
    private String instrumentId; // Instrument performing the analysis

    @Field("cassette_id")
    private String cassetteId; // Cassette containing this sample

    @Field("status")
    private SampleStatus status; // Current status of the sample

    @Field("is_test_order_auto_created")
    private boolean testOrderAutoCreated = false; // Flag if test order was auto-created

    @Field("skip_reason")
    private String skipReason; // Reason if sample was skipped

    @Field("notification_sent")
    private boolean notificationSent = false; // Flag to track if notification was sent
}
