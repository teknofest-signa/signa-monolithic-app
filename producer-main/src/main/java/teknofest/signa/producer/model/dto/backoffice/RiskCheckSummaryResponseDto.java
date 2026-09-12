package teknofest.signa.producer.model.dto.backoffice;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class RiskCheckSummaryResponseDto {

    UUID id;
    String userAccountId;
    String userName;
    String userIban;
    BigDecimal transactionAmount;
    String transactionCurrency;
    String recipientName;
    String recipientType;
    String recipientReference;
    boolean approved;
    String reason;
    BigDecimal riskScore;
    Instant requestedAt;
    Instant createdAt;
}
