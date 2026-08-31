package teknofest.signa.producer.model.dto.transaction;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;
import teknofest.signa.producer.enums.TransactionStatus;
import teknofest.signa.producer.enums.TransactionType;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class TransactionInfo {

    UUID id;

    UUID fromAccountId;
    UUID toAccountId;

    BigDecimal amount;
    BigDecimal fraudScore;

    TransactionType transactionType;

    TransactionStatus transactionStatus;

    String referenceId;

    String description;

    String currency;

    Instant createdAt;
    Instant updatedAt;
}
