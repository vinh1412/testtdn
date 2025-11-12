package fit.test_order_service.repositories;

import fit.test_order_service.entities.TestResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface TestResultRepository extends JpaRepository<TestResult, String> {


    @Query("""
            SELECT CASE WHEN COUNT(tr) > 0 THEN true ELSE false END
            FROM TestResult tr
            WHERE tr.resultId = :testResultId       
    """)
    boolean existsByResultId(String testResultId);

}
