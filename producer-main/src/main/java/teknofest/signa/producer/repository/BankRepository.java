package teknofest.signa.producer.repository;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import teknofest.signa.producer.model.entity.Bank;

@Repository
public interface BankRepository extends JpaRepository<Bank, UUID> {

    Optional<Bank> findByClientId(String clientId);
}
