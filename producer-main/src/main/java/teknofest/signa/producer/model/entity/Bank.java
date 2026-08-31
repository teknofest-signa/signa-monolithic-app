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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "banks")
@FieldDefaults(level = AccessLevel.PRIVATE)
@EntityListeners(AuditingEntityListener.class)
public class Bank {

    @Id
    @UuidGenerator
    @GeneratedValue
    UUID id;

    @Column(name = "name", nullable = false)
    String name;

    /**
     * Public identifier the bank presents on the machine-to-machine API.
     * Not a secret; it names the caller so the API key can be looked up.
     */
    @Column(name = "client_id", nullable = false, unique = true)
    String clientId;

    /**
     * SHA-256 of the bank's API key, hex encoded. The key itself is shown once
     * at issue time and never stored, so a dump of this table does not let an
     * attacker call the OPRF as a member bank.
     */
    @Column(name = "api_key_hash")
    String apiKeyHash;

    @Column(name = "api_key_rotated_at")
    Instant apiKeyRotatedAt;

    /**
     * Kill switch for a single bank's OPRF access, so a suspected compromise or
     * an enumeration attempt can be cut off without deleting the institution
     * and its customer records.
     */
    @Builder.Default
    @Column(name = "oprf_enabled", nullable = false)
    boolean oprfEnabled = true;

    @JdbcTypeCode(SqlTypes.VARBINARY)
    @Column(name = "logo")
    byte[] logo;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    Instant updatedAt;
}
