package teknofest.signa.producer.config;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import teknofest.signa.producer.crypto.Oprf;
import teknofest.signa.producer.crypto.P256Group;
import teknofest.signa.producer.crypto.VoprfServer;
import teknofest.signa.producer.crypto.key.OprfKey;
import teknofest.signa.producer.crypto.key.OprfKeyRing;

/**
 * Loads the OPRF key ring at startup, and refuses to start rather than fall
 * back to anything weaker.
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(OprfProperties.class)
public class OprfKeyConfig {

    private static final String DEVELOPMENT_PROFILE = "dev";

    /**
     * Fixed seed for the development key. This value is in the source tree, so
     * the key it derives is public knowledge and offers no privacy whatsoever.
     * It exists so that pseudonyms stay stable across restarts on a developer
     * machine; a freshly random key each boot would invalidate every row in the
     * local database on every restart.
     */
    private static final byte[] DEVELOPMENT_SEED =
            Oprf.ascii("signa-development-key-do-not-use!");

    @Bean
    public OprfKeyRing oprfKeyRing(OprfProperties properties, Environment environment) {
        OprfKey activeKey = loadActiveKey(properties, environment);

        List<OprfKey> previousKeys = new ArrayList<>();
        for (String encoded : properties.getPreviousKeys()) {
            if (encoded != null && !encoded.isBlank()) {
                previousKeys.add(OprfKey.fromSecret(decodeScalar(encoded, "application.security.oprf.previous-keys")));
            }
        }

        OprfKeyRing keyRing = new OprfKeyRing(activeKey, previousKeys);
        log.info("OPRF key ring loaded. Suite={}, activeKeyId={}, publicKey={}, retainedKeys={}",
                Oprf.CIPHERSUITE_IDENTIFIER, keyRing.activeKeyId(), activeKey.publicKeyHex(), previousKeys.size());

        return keyRing;
    }

    private OprfKey loadActiveKey(OprfProperties properties, Environment environment) {
        String configured = properties.getActiveKey();

        if (configured != null && !configured.isBlank()) {
            return OprfKey.fromSecret(decodeScalar(configured, "application.security.oprf.active-key"));
        }

        if (!isDevelopmentProfileActive(environment)) {
            throw new IllegalStateException(buildMissingKeyMessage());
        }

        BigInteger developmentSecret = VoprfServer.deriveSecretKey(
                Oprf.hash(DEVELOPMENT_SEED), Oprf.ascii("signa-dev"));
        OprfKey key = OprfKey.fromSecret(developmentSecret);

        log.warn("""

                ============================================================
                 OPRF is running on the PUBLIC DEVELOPMENT KEY.
                 Its value is derived from a seed committed to this
                 repository, so every pseudonym it produces can be
                 recomputed by anyone. Never point a deployment holding
                 real customer data at this key.
                 Set SIGNA_OPRF_ACTIVE_KEY to a real key before release.
                 keyId={}
                ============================================================
                """, key.keyId());

        return key;
    }

    private boolean isDevelopmentProfileActive(Environment environment) {
        return Arrays.asList(environment.getActiveProfiles()).contains(DEVELOPMENT_PROFILE);
    }

    private BigInteger decodeScalar(String encoded, String propertyName) {
        BigInteger scalar;
        try {
            scalar = P256Group.decodeScalar(Oprf.fromHex(encoded.trim()));
        } catch (RuntimeException exception) {
            throw new IllegalStateException(
                    propertyName + " must be a P-256 scalar encoded as 64 hex characters", exception);
        }
        if (scalar.signum() == 0) {
            throw new IllegalStateException(propertyName + " must not be zero");
        }
        return scalar;
    }

    /**
     * Startup failures on missing key material are the ones most likely to be
     * "fixed" by pasting whatever gets the server running, so this message
     * hands over a usable key instead of leaving the reader to invent one.
     */
    private String buildMissingKeyMessage() {
        String freshKey = Oprf.toHex(P256Group.serializeScalar(P256Group.randomScalar()));

        return """
                No OPRF server key is configured.

                Set the SIGNA_OPRF_ACTIVE_KEY environment variable (or the
                application.security.oprf.active-key property) to a P-256 scalar
                in 64 hex characters. Here is a freshly generated one:

                  SIGNA_OPRF_ACTIVE_KEY=%s

                Treat it like a signing key: it belongs in a secret manager, not
                in application.yaml or a Dockerfile. Whoever holds it can compute
                the pseudonym of any identifier they can guess.

                For local development only, activate the 'dev' profile
                (SPRING_PROFILES_ACTIVE=dev) to run on the public development key.
                """.formatted(freshKey);
    }
}
