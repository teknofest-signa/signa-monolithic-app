package teknofest.signa.producer.model.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
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

@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "transaction_risk_checks")
@FieldDefaults(level = AccessLevel.PRIVATE)
@EntityListeners(AuditingEntityListener.class)
public class TransactionRiskCheck {

    @Id
    @UuidGenerator
    @GeneratedValue
    UUID id;

    @Column(name = "user_account_id")
    String userAccountId;

    @Column(name = "user_name")
    String userName;

    @Column(name = "user_iban")
    String userIban;

    @Column(name = "user_account_type")
    String userAccountType;

    @Column(name = "user_member_since")
    String userMemberSince;

    @Column(name = "account_current_balance", precision = 19, scale = 4)
    BigDecimal accountCurrentBalance;

    @Column(name = "account_currency")
    String accountCurrency;

    @Column(name = "transaction_amount", precision = 19, scale = 4)
    BigDecimal transactionAmount;

    @Column(name = "transaction_currency")
    String transactionCurrency;

    @Column(name = "transaction_note", length = 500)
    String transactionNote;

    @Column(name = "recipient_type")
    String recipientType;

    @Column(name = "recipient_name")
    String recipientName;

    @Column(name = "recipient_reference")
    String recipientReference;

    @Column(name = "requested_at")
    Instant requestedAt;

    @Column(name = "approved", nullable = false)
    boolean approved;

    @Column(name = "reason", length = 500)
    String reason;

    @Column(name = "risk_score", precision = 19, scale = 4)
    BigDecimal riskScore;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    Instant createdAt;

    @Builder.Default
    @OneToMany(mappedBy = "riskCheck", cascade = CascadeType.ALL, orphanRemoval = true)
    List<TransactionRiskCheckHistoryEntry> historyEntries = new ArrayList<>();
}
