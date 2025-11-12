/*
 * @ (#) Cassette.java    1.0    12/11/2025
 * Copyright (c) 2025 IUH. All rights reserved.
 */
package fit.instrument_service.entities;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.LocalDateTime;

/**
 * @description: Cassette entity for managing multiple cassettes in queue
 * @author: GitHub Copilot
 * @date: 12/11/2025
 * @version: 1.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "cassettes")
public class Cassette extends BaseDocument {

    @Id
    private String id;

    @Field("cassette_identifier")
    @Indexed(unique = true)
    private String cassetteIdentifier; // Unique identifier for the cassette

    @Field("instrument_id")
    @Indexed
    private String instrumentId; // Instrument that will process this cassette

    @Field("queue_position")
    private Integer queuePosition; // Position in the processing queue

    @Field("is_processed")
    private boolean processed = false; // Flag if cassette has been processed

    @Field("processed_at")
    private LocalDateTime processedAt; // Time when processing completed

    @Field("workflow_id")
    private String workflowId; // Associated workflow ID
}
