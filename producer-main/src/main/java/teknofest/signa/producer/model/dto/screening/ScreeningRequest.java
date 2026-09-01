package teknofest.signa.producer.model.dto.screening;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

/**
 * A pre-transaction check: is the person behind this pseudonym flagged
 * anywhere in the network?
 *
 * <p>This is the second of the two calls a member bank makes, and the one that
 * actually answers the question. It cannot be folded into
 * {@code /api/v1/oprf/evaluate}: there the server sees only {@code r·P} and
 * cannot strip the bank's blind, so it has no pseudonym to match on. Only the
 * bank can unblind, which is why it is the bank that comes back with the
 * pseudonym here.
 *
 * <p>Nothing in this request can carry an identifier. The single field is a
 * fixed-width digest the server can compare and cannot invert, validated to
 * that shape so a caller that skipped the OPRF is rejected at the door rather
 * than having a raw FIN read out of a request log later.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ScreeningRequest {

    /** OPRF output, hex encoded. Same 64 characters that enrolment carries. */
    @NotBlank(message = "Pseudonym cannot be empty!")
    @Pattern(regexp = "^[0-9a-f]{64}$",
            message = "Pseudonym must be the 64-character hex OPRF output. Send the pseudonym, never the FIN!")
    String pseudonym;

    /**
     * The key the pseudonym was derived under. Pseudonyms are comparable only
     * within one key, so a check that left this out would quietly match
     * nothing and report every person as clear.
     */
    @NotBlank(message = "OPRF key id cannot be empty!")
    @Pattern(regexp = "^[0-9a-f]{16}$", message = "Key id must be 16 hex characters!")
    String oprfKeyId;
}
