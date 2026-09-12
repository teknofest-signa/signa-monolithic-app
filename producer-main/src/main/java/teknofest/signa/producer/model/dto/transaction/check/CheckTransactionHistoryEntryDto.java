package teknofest.signa.producer.model.dto.transaction.check;

import java.math.BigDecimal;
import java.time.Instant;
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
public class CheckTransactionHistoryEntryDto {

    String name;
    String category;
    Instant date;
    BigDecimal amt;
    String initials;
}
