package teknofest.signa.producer.repository;

import java.time.Instant;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import teknofest.signa.producer.model.entity.ScreeningCheck;

@Repository
public interface ScreeningCheckRepository extends JpaRepository<ScreeningCheck, UUID> {

    /**
     * How many checks a bank has run since a point in time. Outlives the
     * in-memory rate limiter, which is where a bank grinding through the
     * identifier space slowly enough to stay under the per-minute ceiling
     * becomes visible.
     */
    @Query("""
            SELECT COUNT(c)
            FROM ScreeningCheck c
            WHERE c.bankId = :bankId AND c.accepted = true AND c.createdAt >= :since
            """)
    long countAcceptedSince(@Param("bankId") UUID bankId, @Param("since") Instant since);
}
