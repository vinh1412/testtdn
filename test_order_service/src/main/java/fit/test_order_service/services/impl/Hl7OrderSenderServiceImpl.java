/*
 * @ {#} Hl7OrderSenderServiceImpl.java   1.0     11/11/2025
 *
 * Copyright (c) 2025 IUH. All rights reserved.
 */

package fit.test_order_service.services.impl;

import fit.test_order_service.dtos.request.Hl7MessageRequest;
import fit.test_order_service.dtos.response.Hl7ProcessResponse;
import fit.test_order_service.entities.TestOrder;
import fit.test_order_service.entities.TestOrderItem;
import fit.test_order_service.exceptions.NotFoundException;
import fit.test_order_service.repositories.TestOrderRepository;
import fit.test_order_service.services.Hl7OrderSenderService;
import fit.test_order_service.services.Hl7ParserService;
import fit.test_order_service.services.Hl7ProcessingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

/*
 * @description: Implementation of HL7 order sender service
 * @author: Tran Hien Vinh
 * @date:   11/11/2025
 * @version:    1.0
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class Hl7OrderSenderServiceImpl implements Hl7OrderSenderService {
    private final Hl7ParserService hl7ParserService;

    private final Hl7ProcessingService hl7ProcessService;

    private final TestOrderRepository testOrderRepository;

    @Override
    public Hl7ProcessResponse sendOrderAndProcessResult(String testOrderId) {
        // Lấy order và items
        TestOrder order = testOrderRepository.findById(testOrderId)
                .orElseThrow(() -> new NotFoundException("Test order not found: " + testOrderId));
        List<TestOrderItem> items = order.getItems();

        // Gửi order đi và nhận về HL7 response
        String hl7Response = sendOrderToInstrument(order, items);

        // Tạo request object cho processHl7Message
        Hl7MessageRequest hl7Request = new Hl7MessageRequest();
        hl7Request.setHl7Payload(hl7Response);

        // Gọi xử lý message HL7 raw nhận được
        return hl7ProcessService.processHl7Message(hl7Request);
    }

    @Override
    public String sendOrderToInstrument(TestOrder order, List<TestOrderItem> items) {
        // Xây dựng HL7 từ order và items
        String payload = hl7ParserService.buildHl7OrderMessage(order, items);
        log.info("[HL7] Sending OML^O21 for order {}...", order.getOrderCode());
        log.debug("HL7 request payload:\n{}", payload);

        // Nếu chưa có kết nối, mock HL7 response
        if (isMockMode()) {
            log.info("[MOCK] Simulating analyzer response for order {}", order.getOrderCode());
            return buildMockHl7Response(order, items);
        }

//        // Nếu có kết nối thật
//        String framed = (char) 0x0B + payload + (char) 0x1C + (char) 0x0D;
//        String response = hl7SocketClient.send(framed);
//        log.info("[HL7] Received raw response:\n{}", response);
//        return response;
        return null;
    }

    // Kiểm tra chế độ mock (dev)
    private boolean isMockMode() {
        return true; // dùng mock mặc định cho dev
    }


    /**
     * Giả lập HL7 ORU^R01 (phản hồi kết quả từ máy)
     */
    private String buildMockHl7Response(TestOrder order, List<TestOrderItem> items) {
        // Xây dựng tin nhắn HL7 ORU^R01
        StringBuilder hl7 = new StringBuilder();

        // MSH Segment
        hl7.append("MSH|^~\\&|INSTRUMENT|LAB|TEST-ORDER|SYSTEM|")
                .append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")))
                .append("||ORU^R01|MSG-MOCK-").append(UUID.randomUUID().toString().substring(0, 8))
                .append("|P|2.5\r");

        // PID Segment
        hl7.append("PID|1||")
                .append(order.getMedicalRecordId()).append("||")
                .append(order.getFullName()).append("||")
                .append(order.getDateOfBirth().format(DateTimeFormatter.ofPattern("yyyyMMdd"))).append("|")
                .append(order.getGender().name().charAt(0)).append("|||")
                .append(order.getAddress() != null ? order.getAddress() : "UNKNOWN")
                .append("||").append(order.getPhone()).append("\r");

        // OBR Segment
        hl7.append("OBR|1||")
                .append(order.getOrderId()).append("|PANEL^Complete Blood Count|||")
                .append(order.getCreatedAt().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")))
                .append("\r");

        int index = 1;
        for (TestOrderItem item : items) {
            String code = item.getTestCode().toUpperCase();
            String testName = item.getTestName();

            // Mặc định
            String value = String.format("%.1f", 10 + Math.random() * 5);
            String unit = "mg/dL";
            String refRange = "";
            String flag = "N"; // Normal

            switch (code) {
                case "GLU" -> { value = "92";  unit = "mg/dL";  refRange = "70-100"; }
                case "HGB" -> { value = "14.2"; unit = "g/dL";  refRange = "13.5-17.5"; }
                case "WBC" -> { value = "6.8";  unit = "10^3/uL"; refRange = "4.5-11.0"; }
                case "PLT" -> { value = "265";  unit = "10^3/uL"; refRange = "150-400"; }
                case "RBC" -> { value = "4.9";  unit = "10^6/uL"; refRange = "4.2-5.9"; }
                case "HCT" -> { value = "45";   unit = "%";      refRange = "38-50"; }
                case "CRE" -> { value = "0.9";  unit = "mg/dL";  refRange = "0.6-1.3"; }
                case "BUN" -> { value = "15";   unit = "mg/dL";  refRange = "7-20"; }
                case "HDL" -> { value = "55";   unit = "mg/dL";  refRange = ">40"; }
                case "LDL" -> { value = "120";  unit = "mg/dL";  refRange = "<130"; }
                case "CHOL" -> { value = "180"; unit = "mg/dL";  refRange = "<200"; }
                case "ALT" -> { value = "25";   unit = "U/L";    refRange = "7-56"; }
                case "AST" -> { value = "30";   unit = "U/L";    refRange = "10-40"; }
                case "ALP" -> { value = "110";  unit = "U/L";    refRange = "44-147"; }
                case "CA"  -> { value = "9.2";  unit = "mg/dL";  refRange = "8.6-10.3"; }
                case "NA"  -> { value = "140";  unit = "mmol/L"; refRange = "135-145"; }
                case "K"   -> { value = "4.2";  unit = "mmol/L"; refRange = "3.5-5.1"; }
                case "CL"  -> { value = "103";  unit = "mmol/L"; refRange = "98-107"; }
                case "CO2" -> { value = "25";   unit = "mmol/L"; refRange = "22-29"; }
                case "TP"  -> { value = "7.0";  unit = "g/dL";   refRange = "6.3-7.9"; }
            }

            // Sinh cờ bất thường (High/Low) ngẫu nhiên nhỏ
            double chance = Math.random();
            if (chance < 0.1) flag = "H";
            else if (chance > 0.9) flag = "L";

            hl7.append(String.format(
                    "OBX|%d|NM|%s^%s|%d|%s|%s|%s|%s|||F\r",
                    index++, code, testName, index - 1, value, unit, refRange, flag
            ));
        }

        return hl7.toString();
    }
}
