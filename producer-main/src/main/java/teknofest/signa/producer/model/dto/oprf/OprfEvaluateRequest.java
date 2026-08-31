package teknofest.signa.producer.model.dto.oprf;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

/**
 * A batch of blinded elements submitted by a member bank for evaluation.
 *
 * <p>Every element is a P-256 point in SEC1 compressed form, hex encoded: 33
 * bytes, 66 hex characters. Nothing in this request can carry a customer
 * identifier — the field is typed and validated as a curve point, so there is
 * no shape in which plaintext could arrive here even by mistake.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class OprfEvaluateRequest {

    @NotEmpty(message = "At least one blinded element is required!")
    @Size(max = 512, message = "Batch is too large!")
    List<@Pattern(regexp = "^0[23][0-9a-fA-F]{64}$",
            message = "Each blinded element must be a compressed P-256 point in hex!") String> blindedElements;

    /**
     * Optional. Names the key to evaluate under, for banks recomputing
     * pseudonyms enrolled before a rotation. Defaults to the active key.
     */
    @Pattern(regexp = "^[0-9a-f]{16}$", message = "Key id must be 16 hex characters!")
    String keyId;
}
