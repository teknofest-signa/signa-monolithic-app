package teknofest.signa.producer.model.dto.bank;

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
public class BankInfo {
    UUID id;

    String name;

    /** Public caller identifier. The API key itself is never returned. */
    String clientId;

    boolean oprfEnabled;

    byte[] logo;

    Instant createdAt;
    Instant updatedAt;
}
