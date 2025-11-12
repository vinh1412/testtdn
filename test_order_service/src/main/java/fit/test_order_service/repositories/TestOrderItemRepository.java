/*
 * @ (#) TestOrderItemRepository.java    1.0    16/10/2025
 * Copyright (c) 2025 IUH. All rights reserved.
 */
package fit.test_order_service.repositories;/*
 * @description:
 * @author: Bao Thong
 * @date: 16/10/2025
 * @version: 1.0
 */

import fit.test_order_service.entities.TestOrderItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TestOrderItemRepository extends JpaRepository<TestOrderItem, String> {
    Optional<TestOrderItem> findByItemIdAndDeletedFalse(String itemId);

    /**
     * Tìm một TestOrderItem theo itemId và orderId, chỉ trả về nếu chưa bị xóa.
     * @param itemId ID của TestOrderItem cần tìm.
     * @param orderId ID của TestOrder cha.
     * @return Optional chứa TestOrderItem nếu tìm thấy.
     */
    Optional<TestOrderItem> findByItemIdAndOrderRefOrderIdAndDeletedFalse(String itemId, String orderId);

    /**
     * Tìm một TestOrderItem theo orderId và itemId, chỉ trả về nếu chưa bị xóa.
     * @param orderId ID của TestOrder cha.
     * @param itemId ID của TestOrderItem cần tìm.
     * @return Optional chứa TestOrderItem nếu tìm thấy.
     */
    Optional<TestOrderItem> findByOrderRefOrderIdAndItemIdAndDeletedFalse(String orderId, String itemId);

    Optional<TestOrderItem> findByOrderRefOrderIdAndTestCode(String orderId, String testCode);
}

