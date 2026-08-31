package teknofest.signa.producer.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
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
import teknofest.signa.producer.enums.TransactionStatus;
import teknofest.signa.producer.enums.TransactionType;

@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "transactions")
@FieldDefaults(level = AccessLevel.PRIVATE)
@EntityListeners(AuditingEntityListener.class)
public class Transaction {

    @Id
    @UuidGenerator
    @GeneratedValue
    UUID id;

    @Column(name = "from_account_id", nullable = false)
    UUID fromAccountId;

    @Column(name = "to_account_id", nullable = false)
    UUID toAccountId;

    @Column(name = "amount", nullable = false, precision = 19, scale = 4)
    BigDecimal amount;

    @Column(name = "currency", nullable = false)
    String currency;

    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", nullable = false)
    TransactionType transactionType;

    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_status", nullable = false)
    TransactionStatus transactionStatus;

    @Column(name = "reference_id", unique = true)
    String referenceId;

    @Column(name = "description")
    String description;

    @Column(name = "fraud_score", nullable = false, precision = 19, scale = 4)
    BigDecimal fraudScore;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    Instant updatedAt;
}
