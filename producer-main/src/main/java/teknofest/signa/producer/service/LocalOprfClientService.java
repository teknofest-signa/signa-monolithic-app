package teknofest.signa.producer.service;

import jakarta.annotation.PostConstruct;
import java.util.Arrays;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import teknofest.signa.producer.crypto.Oprf;
import teknofest.signa.producer.crypto.P256Group;
import teknofest.signa.producer.crypto.PiiNormalizer;
import teknofest.signa.producer.crypto.VoprfClient;
import teknofest.signa.producer.crypto.VoprfServer;
import teknofest.signa.producer.crypto.key.OprfKey;
import teknofest.signa.producer.crypto.key.OprfKeyRing;

/**
 * Runs the <b>bank's</b> half of the protocol inside the server, for
 * development and demonstration only.
 *
 * <p>Read that again before using it: this class accepts a raw identifier at
 * the server, which is exactly what the OPRF layer exists to prevent. It is
 * here so the platform can be demonstrated before the bank connectors exist,
 * and so the reference client has a runnable path. It does not weaken the
 * protocol — the maths is identical — but it moves the trust boundary to the
 * wrong side of the network, and any deployment holding real customer data must
 * leave it off.
 *
 * <p>The bean does not exist unless
 * {@code application.security.oprf.local-client.enabled} is true, and it
 * refuses to start under the {@code prod} profile.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "application.security.oprf.local-client", name = "enabled", havingValue = "true")
public class LocalOprfClientService {

    private final OprfKeyRing keyRing;
    private final Environment environment;

    @PostConstruct
    void warnAndGuard() {
        if (Arrays.asList(environment.getActiveProfiles()).contains("prod")) {
            throw new IllegalStateException("""
                    application.security.oprf.local-client.enabled is true under the 'prod' profile.

                    This endpoint accepts raw customer identifiers at the central server and
                    performs the bank's half of the OPRF there, which defeats the purpose of
                    the privacy layer. Turn it off, and derive pseudonyms in the bank connector.
                    """);
        }

        log.warn("""

                ============================================================
                 The local OPRF client is ENABLED.
                 /api/v1/oprf/local/derive accepts RAW IDENTIFIERS at the
                 server. This is a development convenience only. Real
                 deployments derive pseudonyms inside the bank, so that the
                 identifier never crosses the network.
                ============================================================
                """);
    }

    /**
     * Full client flow for one identifier: canonicalise, blind, evaluate,
     * verify the proof, unblind, hash.
     *
     * <p>Nothing derived here is persisted or logged. The identifier lives only
     * for the duration of the call.
     */
    public Result derivePseudonym(PiiNormalizer.IdentifierType type, String rawIdentifier) {
        byte[] input = PiiNormalizer.canonicalize(type, rawIdentifier);
        OprfKey key = keyRing.activeKey();

        VoprfClient.BlindResult blindResult = VoprfClient.blind(input);

        VoprfServer.BlindEvaluateResult evaluation = VoprfServer.blindEvaluate(
                key.secretKey(), key.publicKey(), List.of(blindResult.blindedElement()));

        // Verified rather than assumed, even though both halves run in this
        // process: the reference client must demonstrate the check a real bank
        // has to perform, not quietly skip it.
        byte[] pseudonym = VoprfClient.finalize(
                input,
                blindResult.blind(),
                evaluation.evaluatedElements().getFirst(),
                blindResult.blindedElement(),
                key.publicKey(),
                evaluation.proof());

        return new Result(Oprf.toHex(pseudonym), key.keyId(), key.publicKeyHex(),
                Oprf.toHex(P256Group.serializeElement(blindResult.blindedElement())));
    }

    public record Result(String pseudonym, String keyId, String publicKey, String blindedElement) {
    }
}
