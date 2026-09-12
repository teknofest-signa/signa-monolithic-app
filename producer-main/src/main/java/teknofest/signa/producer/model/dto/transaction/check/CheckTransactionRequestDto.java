package teknofest.signa.producer.model.dto.transaction.check;

import java.time.Instant;
import java.util.List;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CheckTransactionRequestDto {

    CheckTransactionUserDto user;
    CheckTransactionAccountDto account;
    CheckTransactionDetailsDto transaction;
    List<CheckTransactionHistoryEntryDto> transactionHistory;
    List<CheckTransactionHistoryEntryDto> depositHistory;
    List<CheckTransactionHistoryEntryDto> withdrawalHistory;
    Instant requestedAt;
}
