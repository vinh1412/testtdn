package fit.warehouse_service.repositories;

import fit.warehouse_service.entities.ReagentLot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ReagentLotRepository extends JpaRepository<ReagentLot, String> {
    ReagentLot findByLotNumber(String lotNumber);
}