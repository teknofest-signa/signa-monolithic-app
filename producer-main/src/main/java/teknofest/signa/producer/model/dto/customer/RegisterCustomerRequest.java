package teknofest.signa.producer.model.dto.customer;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

/**
 * Enrolment of a customer into the shared fraud network.
 *
 * <p>This request used to carry the customer's FIN, which meant the central
 * server received and stored the national identity number of every customer of
 * every member bank. It now carries a pseudonym the bank derived through the
 * OPRF, and there is no field here that can hold an identifier: {@code name} is
 * a display label the bank chooses, and {@code pseudonym} is a fixed-width hex
 * digest that the server can compare but cannot invert.
 *
 * <p>Deriving the pseudonym is the bank's job, in three calls:
 * blind the identifier locally, POST the blinded point to
 * {@code /api/v1/oprf/evaluate}, verify the proof and unblind. See
 * {@code docs/oprf-layer.md}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class RegisterCustomerRequest {

    @NotBlank(message = "Name cannot be empty!")
    String name;

    /**
     * OPRF output, hex encoded. Fixed at 64 characters: a request that is any
     * other length did not come out of the protocol, and rejecting it here
     * stops a raw identifier from being stored in this column by a caller that
     * skipped the OPRF.
     */
    @NotBlank(message = "Pseudonym cannot be empty!")
    @Pattern(regexp = "^[0-9a-f]{64}$",
            message = "Pseudonym must be the 64-character hex OPRF output. Send the pseudonym, never the FIN!")
    String pseudonym;

    /** Identifier of the OPRF key the pseudonym was derived under. */
    @NotBlank(message = "OPRF key id cannot be empty!")
    @Pattern(regexp = "^[0-9a-f]{16}$", message = "Key id must be 16 hex characters!")
    String oprfKeyId;

    @NotNull(message = "BankId cannot be empty!")
    UUID bankId;
}
