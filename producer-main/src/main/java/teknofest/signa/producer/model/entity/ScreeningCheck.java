package teknofest.signa.producer.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;
import org.hibernate.annotations.UuidGenerator;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * Audit record for one screening check.
 *
 * <p>Holds who asked, when, under which key, and what they were told. It does
 * not hold the pseudonym. A table of "bank X asked about pseudonym Y" would be
 * a linkage database assembled out of the audit trail — precisely the artefact
 * the OPRF exists to prevent anyone, this server included, from accumulating.
 *
 * <p>What it is for: screening is the platform's other oracle. Paired with an
 * evaluation, a caller can take a candidate identifier, derive its pseudonym
 * and ask whether that person is flagged. The rate limiter bounds how fast
 * that can be done; this table is what makes it visible afterwards, because
 * routine pre-transaction screening and a walk through the identifier space
 * have very different shapes over time.
 */
@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "screening_checks")
@FieldDefaults(level = AccessLevel.PRIVATE)
@EntityListeners(AuditingEntityListener.class)
public class ScreeningCheck {

    @Id
    @UuidGenerator
    @GeneratedValue
    UUID id;

    @Column(name = "bank_id", nullable = false)
    UUID bankId;

    @Column(name = "key_id", nullable = false)
    String keyId;

    /** False when the check was refused, by quota or by an unknown key. */
    @Column(name = "accepted", nullable = false)
    boolean accepted;

    /** Null when the check was refused; otherwise whether the answer was FLAGGED. */
    @Column(name = "flagged")
    Boolean flagged;

    /** Null when the check was accepted; the limit or check that failed otherwise. */
    @Column(name = "rejection_reason")
    String rejectionReason;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    Instant createdAt;
}
