/*
 * @ (#) TestOrderItem.java    1.0    11/10/2025
 * Copyright (c) 2025 IUH. All rights reserved.
 */
package fit.test_order_service.entities;/*
 * @description:
 * @author: Bao Thong
 * @date: 11/10/2025
 * @version: 1.0
 */

import fit.test_order_service.enums.ItemStatus;
import fit.test_order_service.utils.TestOrderGenerator;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "test_order_item", indexes = {
        @Index(name = "idx_item_order", columnList = "order_id"),
        @Index(name = "idx_item_code", columnList = "test_code")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TestOrderItem {
    @Id
    @Column(name = "item_id", length = 36, nullable = false, updatable = false)
    private String itemId;

    @NotBlank
    @Size(max = 64)
    @Column(name = "test_code", length = 64, nullable = false)
    private String testCode;

    @NotBlank
    @Size(max = 128)
    @Column(name = "test_name", length = 128, nullable = false)
    private String testName;

    @Column(name = "unit", length = 32)
    private String unit;

    @Column(name = "reference_range", length = 64)
    private String referenceRange;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 16, nullable = false)
    private ItemStatus status;

    @Column(name = "created_at", columnDefinition = "datetime(6)")
    private LocalDateTime createdAt;

    @Column(name = "created_by", length = 36)
    private String createdBy;

    @Column(name = "updated_at", columnDefinition = "datetime(6)")
    private LocalDateTime updatedAt;

    @Column(name = "updated_by", length = 36)
    private String updatedBy;

    // soft delete
    @Column(name = "deleted", nullable = false)
    @Builder.Default
    private boolean deleted = false;

    @Column(name = "deleted_at", columnDefinition = "datetime(6)")
    private LocalDateTime deletedAt;

    @Column(name = "deleted_by", length = 36)
    private String deletedBy;


    /* Relations */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", referencedColumnName = "order_id",
            foreignKey = @ForeignKey(name = "fk_item_order"), updatable = false)
    private TestOrder orderRef;

    @OneToMany(mappedBy = "itemRef", fetch = FetchType.LAZY)
    private List<TestResult> results;

    @PrePersist
    void pp() {
        if (itemId == null) {
            itemId = TestOrderGenerator.generateTestItemId();
        }
        if (status == null) {
            status = ItemStatus.PENDING;
        }
        if (createdAt == null) {
            createdAt = LocalDateTime.now(ZoneOffset.UTC);
        }
    }

    @PreUpdate
    void pu() {
        updatedAt = LocalDateTime.now(ZoneOffset.UTC);
    }
}
