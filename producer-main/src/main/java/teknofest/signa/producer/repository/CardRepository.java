package teknofest.signa.producer.repository;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import teknofest.signa.producer.model.entity.Card;

@Repository
public interface CardRepository extends JpaRepository<Card, UUID> {
}
