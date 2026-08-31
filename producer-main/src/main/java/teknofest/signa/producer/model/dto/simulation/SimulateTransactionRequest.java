package teknofest.signa.producer.model.dto.simulation;

import jakarta.validation.constraints.NotNull;
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
import teknofest.signa.producer.enums.TransactionType;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class SimulateTransactionRequest {

    @NotNull(message = "Simulation type cannot be blank!")
    SimulationType simulationType;

    UUID fromAccountId;
    UUID toAccountId;

    BigDecimal amount;

    String currency;

    TransactionType transactionType;

    TransactionChannel transactionChannel;

    Instant transactionTime;

    boolean isNewBeneficiary;
    boolean isCrossBorderTransaction;
}
