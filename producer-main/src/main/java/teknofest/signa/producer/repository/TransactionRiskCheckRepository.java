package teknofest.signa.producer.repository;

import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import teknofest.signa.producer.model.entity.TransactionRiskCheck;

@Repository
public interface TransactionRiskCheckRepository extends JpaRepository<TransactionRiskCheck, UUID> {

    Page<TransactionRiskCheck> findByApproved(boolean approved, Pageable pageable);
}
