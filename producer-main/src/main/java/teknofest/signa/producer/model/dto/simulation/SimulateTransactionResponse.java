package teknofest.signa.producer.model.dto.simulation;

import java.math.BigDecimal;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;
import teknofest.signa.producer.enums.TransactionFraudStatus;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class SimulateTransactionResponse {

    BigDecimal transactionFraudScore;

    TransactionFraudStatus transactionFraudStatus;
}
