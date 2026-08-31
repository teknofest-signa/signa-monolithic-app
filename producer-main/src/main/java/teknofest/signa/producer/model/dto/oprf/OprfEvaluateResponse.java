package teknofest.signa.producer.model.dto.oprf;

import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

/**
 * The server's answer: one evaluated element per submitted element, in the same
 * order, plus a single DLEQ proof covering the whole batch.
 *
 * <p>The bank must verify {@code proof} against {@code publicKey} before
 * unblinding anything. Accepting an evaluation without checking it would let a
 * server that used a different key go undetected.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class OprfEvaluateResponse {

    /** Compressed P-256 points in hex, aligned by index with the request. */
    List<String> evaluatedElements;

    /** Batched DLEQ proof: SerializeScalar(c) || SerializeScalar(s), 128 hex characters. */
    String proof;

    /** The key used, so the bank can tag the pseudonyms it derives. */
    String keyId;

    /** pkS for this key, repeated here so verification needs no second call. */
    String publicKey;

    /** Ciphersuite identifier, for a client that supports more than one. */
    String ciphersuite;
}
