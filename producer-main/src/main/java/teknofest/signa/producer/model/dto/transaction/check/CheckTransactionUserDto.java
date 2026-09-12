package teknofest.signa.producer.model.dto.transaction.check;

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
public class CheckTransactionUserDto {

    String accountId;
    String name;
    String iban;
    String accountType;
    String memberSince;
}
