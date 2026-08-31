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
 * Audit record for one OPRF evaluation request.
 *
 * <p>Deliberately records only <em>who asked, when, under which key, and how
 * many</em>. It holds no blinded elements, no evaluated elements and no
 * pseudonyms, because an audit trail that stored those would recreate the
 * linkage database the protocol exists to avoid.
 *
 * <p>What it is for: enumeration by a member bank looks nothing like normal
 * use. Onboarding is bursty and finite; probing is sustained and large. Without
 * this table a bank walking the identifier space would be invisible after the
 * fact, and the rate limiter alone would only slow it down rather than leave
 * evidence.
 */
@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "oprf_evaluations")
@FieldDefaults(level = AccessLevel.PRIVATE)
@EntityListeners(AuditingEntityListener.class)
public class OprfEvaluation {

    @Id
    @UuidGenerator
    @GeneratedValue
    UUID id;

    @Column(name = "bank_id", nullable = false)
    UUID bankId;

    @Column(name = "key_id", nullable = false)
    String keyId;

    @Column(name = "batch_size", nullable = false)
    int batchSize;

    @Column(name = "accepted", nullable = false)
    boolean accepted;

    /** Null when the request was accepted; the limit or check that failed otherwise. */
    @Column(name = "rejection_reason")
    String rejectionReason;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    Instant createdAt;
}
