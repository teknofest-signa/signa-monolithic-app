package teknofest.signa.producer.model.dto.backoffice;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;
import teknofest.signa.producer.model.dto.transaction.check.CheckTransactionAccountDto;
import teknofest.signa.producer.model.dto.transaction.check.CheckTransactionDetailsDto;
import teknofest.signa.producer.model.dto.transaction.check.CheckTransactionHistoryEntryDto;
import teknofest.signa.producer.model.dto.transaction.check.CheckTransactionUserDto;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class RiskCheckDetailResponseDto {

    UUID id;
    CheckTransactionUserDto user;
    CheckTransactionAccountDto account;
    CheckTransactionDetailsDto transaction;
    boolean approved;
    String reason;
    BigDecimal riskScore;
    List<RiskFactorDetailDto> riskFactors;
    Instant requestedAt;
    Instant createdAt;

    List<CheckTransactionHistoryEntryDto> historyEntries;
    BigDecimal depositTotal;
    BigDecimal withdrawalTotal;
    BigDecimal avgWithdrawal;
    BigDecimal maxWithdrawal;
    BigDecimal projectedBalance;
    Map<String, BigDecimal> categoryBreakdown;
    Map<String, Integer> categoryCountBreakdown;
}
