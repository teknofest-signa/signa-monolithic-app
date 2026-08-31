package teknofest.signa.producer.repository;

import java.time.Instant;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import teknofest.signa.producer.model.entity.OprfEvaluation;

@Repository
public interface OprfEvaluationRepository extends JpaRepository<OprfEvaluation, UUID> {

    /**
     * Total elements a bank has had evaluated since a point in time. Lets an
     * operator see the shape of a bank's usage over a window that outlives the
     * in-memory rate limiter, which is where sustained enumeration shows up.
     */
    @Query("""
            SELECT COALESCE(SUM(e.batchSize), 0)
            FROM OprfEvaluation e
            WHERE e.bankId = :bankId AND e.accepted = true AND e.createdAt >= :since
            """)
    long sumAcceptedElementsSince(@Param("bankId") UUID bankId, @Param("since") Instant since);
}
