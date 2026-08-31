package teknofest.signa.producer.model.dto.oprf;

import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

/**
 * The server's OPRF public parameters.
 *
 * <p>Published so a bank can pin the key it expects and detect a server that
 * starts answering under a different one. Contains no secret: the public key is
 * meant to be widely known, and its exposure is what makes the DLEQ proof
 * checkable.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class OprfPublicKeyResponse {

    String ciphersuite;

    String mode;

    String activeKeyId;

    String activePublicKey;

    /** Superseded keys still accepted for evaluation, for rotation overlap. */
    List<KeyInfo> keys;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @FieldDefaults(level = AccessLevel.PRIVATE)
    public static class KeyInfo {
        String keyId;
        String publicKey;
        boolean active;
    }
}
