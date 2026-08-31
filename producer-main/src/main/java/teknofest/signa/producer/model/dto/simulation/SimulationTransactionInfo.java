package teknofest.signa.producer.model.dto.simulation;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;
import teknofest.signa.producer.enums.SimulationType;
import teknofest.signa.producer.enums.TransactionChannel;
import teknofest.signa.producer.enums.TransactionFraudStatus;
import teknofest.signa.producer.enums.TransactionType;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class SimulationTransactionInfo {

    UUID id;

    SimulationType simulationType;

    UUID fromAccountId;
    UUID toAccountId;

    BigDecimal amount;
    BigDecimal transactionFraudScore;

    TransactionType transactionType;

    TransactionChannel transactionChannel;

    TransactionFraudStatus transactionFraudStatus;

    String currency;

    boolean isNewBeneficiary;
    boolean isCrossBorderTransaction;

    Instant transactionTime;

    Instant createdAt;
    Instant updatedAt;
}
