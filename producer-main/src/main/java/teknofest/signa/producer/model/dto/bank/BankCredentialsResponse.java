package teknofest.signa.producer.model.dto.bank;

import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

/**
 * The machine credentials a member bank uses to reach the OPRF endpoint.
 *
 * <p>This is the only time the API key is ever readable. Only its digest is
 * stored, so a lost key is replaced by rotation, not recovered.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class BankCredentialsResponse {

    UUID bankId;

    String name;

    String clientId;

    /** Shown once. Deliver it over a channel the bank already trusts. */
    String apiKey;

    @Builder.Default
    String notice = "Store this API key now. It is not recoverable; a lost key must be rotated.";
}
