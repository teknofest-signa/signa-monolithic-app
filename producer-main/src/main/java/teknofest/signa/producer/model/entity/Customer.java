package teknofest.signa.producer.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
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
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import teknofest.signa.producer.enums.CustomerStatus;

@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "customers", indexes = {
        @Index(name = "idx_customers_pseudonym_key", columnList = "pseudonym, oprf_key_id")
})
@FieldDefaults(level = AccessLevel.PRIVATE)
@EntityListeners(AuditingEntityListener.class)
public class Customer {

    @Id
    @UuidGenerator
    @GeneratedValue
    UUID id;

    @Column(name = "bank_id", nullable = false)
    UUID bankId;

    @Column(name = "name", nullable = false)
    String name;

    @Column(name = "bank_name", nullable = false)
    String bankName;

    /**
     * The OPRF output for this customer, hex encoded (SHA-256, 64 characters).
     *
     * <p>This replaces what used to be the raw FIN. It is computed inside the
     * bank, from an identifier the central server never sees, and it is stable
     * across banks: two institutions enrolling the same person under the same
     * key arrive at the same value without either of them, or the server,
     * learning anything about the other's customer. That property is the whole
     * basis of cross-institution matching here.
     *
     * <p>It is still a persistent identifier for a natural person, so it is
     * personal data under any sensible reading and is treated as such: not
     * logged, not returned by the customer read APIs.
     */
    @Column(name = "pseudonym", nullable = false, length = 64)
    String pseudonym;

    /**
     * The OPRF key the pseudonym was derived under. Pseudonyms are only
     * comparable within one key, so matching is always scoped by this column;
     * comparing across keys would silently never match.
     */
    @Column(name = "oprf_key_id", nullable = false, length = 32)
    String oprfKeyId;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "customer_status", nullable = false)
    CustomerStatus customerStatus = CustomerStatus.ACTIVE;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    Instant updatedAt;
}
