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
import teknofest.signa.producer.enums.SimulationType;
import teknofest.signa.producer.enums.TransactionChannel;
import teknofest.signa.producer.enums.TransactionFraudStatus;
import teknofest.signa.producer.enums.TransactionType;

@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "simulations")
@FieldDefaults(level = AccessLevel.PRIVATE)
@EntityListeners(AuditingEntityListener.class)
public class Simulation {

    @Id
    @UuidGenerator
    @GeneratedValue
    UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "simulation_type", nullable = false)
    SimulationType simulationType;

    @Column(name = "from_account_id")
    UUID fromAccountId;

    @Column(name = "to_account_id")
    UUID toAccountId;

    @Column(name = "amount", precision = 19, scale = 4)
    BigDecimal amount;

    @Column(name = "currency")
    String currency;

    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type")
    TransactionType transactionType;

    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_channel")
    TransactionChannel transactionChannel;

    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_fraud_status")
    TransactionFraudStatus transactionFraudStatus;

    @Column(name = "transaction_time")
    Instant transactionTime;

    @Column(name = "is_new_beneficiary")
    boolean isNewBeneficiary;

    @Column(name = "is_cross_border_transaction")
    boolean isCrossBorderTransaction;

    @Column(name = "transaction_fraud_score", precision = 19, scale = 4)
    BigDecimal transactionFraudScore;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    Instant updatedAt;
}
