/*
 * @ {#} TestCodeGenerator.java   1.0     22/10/2025
 *
 * Copyright (c) 2025 IUH. All rights reserved.
 */

package fit.test_order_service.utils;

import fit.test_order_service.entities.TestCatalog;
import fit.test_order_service.repositories.TestCatalogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/*
 * @description: Utility class for generating and retrieving test codes
 * @author: Tran Hien Vinh
 * @date:   22/10/2025
 * @version:    1.0
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TestCodeGenerator {

    private final TestCatalogRepository testCatalogRepository;

    /**
     * Tìm local code theo raw test code (có thể là LOINC hoặc local code)
     */
    public String findLocalCodeByTestCode(String testCode) {
        if (testCode == null || testCode.trim().isEmpty()) {
            return null;
        }

        // Thử tìm theo local code trước
        Optional<TestCatalog> byLocal = testCatalogRepository
                .findByLocalCodeIgnoreCaseAndActiveTrue(testCode.trim());
        if (byLocal.isPresent()) {
            return byLocal.get().getLocalCode();
        }

        // Thử tìm theo LOINC code
        Optional<TestCatalog> byLoinc = testCatalogRepository
                .findByLoincCodeAndActiveTrue(testCode.trim());
        if (byLoinc.isPresent()) {
            return byLoinc.get().getLocalCode();
        }

        return null;
    }

    /**
     * Tìm local code theo tên xét nghiệm
     */
    public String findLocalCodeByTestName(String testName) {
        if (testName == null || testName.trim().isEmpty()) {
            return null;
        }

        Optional<TestCatalog> catalog = testCatalogRepository
                .findByTestNameIgnoreCaseAndActiveTrue(testName.trim());

        return catalog.map(TestCatalog::getLocalCode).orElse(null);
    }
}
