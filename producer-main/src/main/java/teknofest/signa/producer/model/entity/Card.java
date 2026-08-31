package teknofest.signa.producer.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import teknofest.signa.producer.enums.CardStatus;
import teknofest.signa.producer.enums.CardType;

@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "cards")
@FieldDefaults(level = AccessLevel.PRIVATE)
@EntityListeners(AuditingEntityListener.class)
public class Card {

    @Id
    @UuidGenerator
    @GeneratedValue
    UUID id;

    @Column(name = "account_id", nullable = false)
    UUID accountId;

    @Column(name = "card_number", unique = true, nullable = false)
    String cardNumber;

    @Column(name = "expiry_month", nullable = false)
    int expiryMonth;

    @Column(name = "expiry_year", nullable = false)
    int expiryYear;

    @Column(name = "cvv")
    String cvv;

    @Enumerated(EnumType.STRING)
    @Column(name = "card_type", nullable = false)
    CardType cardType;

    @Enumerated(EnumType.STRING)
    @Column(name = "card_status", nullable = false)
    CardStatus cardStatus;

    @CreatedDate
    @Column(name = "created_at", updatable = false, nullable = false)
    Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    Instant updatedAt;
}
