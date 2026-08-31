package teknofest.signa.producer.model.dto.customer;

import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;
import teknofest.signa.producer.enums.CustomerStatus;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
/**
 * Customer projection returned by the read APIs.
 *
 * <p>Carries no pseudonym. The pseudonym is a stable cross-bank identifier, and
 * exposing the full set to any authenticated operator would let a client
 * reconstruct the linkage graph the platform is built to keep server-side. The
 * key id is included so an operator can tell which OPRF epoch a record belongs
 * to when planning a rotation.
 */
public class CustomerInfo {

    UUID id;

    UUID bankId;

    String bankName;

    String name;

    String oprfKeyId;

    CustomerStatus customerStatus;

    Instant createdAt;
}
