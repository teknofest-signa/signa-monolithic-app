package teknofest.signa.producer.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import teknofest.signa.producer.crypto.Oprf;
import teknofest.signa.producer.crypto.P256Group;
import teknofest.signa.producer.crypto.key.OprfKeyRing;

/**
 * Startup behaviour of the OPRF key ring.
 *
 * <p>The failure modes here are the quiet ones. A server that boots on a
 * fallback key, or that accepts two different keys under one id, keeps
 * answering requests and produces pseudonyms that are simply wrong — no
 * exception, no alert, just a federation that stops matching.
 */
class OprfKeyConfigTest {

    private static final String KEY_A = "ca5d94c8807817669a51b196c34c1b7f8442fde4334a7121ae4736364312fca6";
    private static final String KEY_B = "159749d750713afe245d2d39ccfaae8381c53ce92d098a9375ee70739c7ac0bf";

    // OprfKeyConfig carries @EnableConfigurationProperties, so the runner needs
    // nothing else to bind OprfProperties.
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(OprfKeyConfig.class);

    @Test
    @DisplayName("refuses to start with no key configured and no dev profile")
    void failsFastWithoutKey() {
        runner.run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure())
                    .rootCause()
                    .isInstanceOf(IllegalStateException.class);
        });
    }

    @Test
    @DisplayName("the failure message hands over a usable key instead of leaving one to be invented")
    void failureMessageIsActionable() {
        runner.run(context -> {
            assertThat(context).hasFailed();
            String message = rootCauseMessage(context.getStartupFailure());

            assertThat(message).contains("SIGNA_OPRF_ACTIVE_KEY=");
            assertThat(message).contains("secret manager");
        });
    }

    @Test
    @DisplayName("loads a configured key and derives its id from the public key")
    void loadsConfiguredKey() {
        runner.withPropertyValues("application.security.oprf.active-key=" + KEY_A).run(context -> {
            assertThat(context).hasNotFailed();
            OprfKeyRing keyRing = context.getBean(OprfKeyRing.class);

            String expectedKeyId = Oprf.toHex(Oprf.hash(P256Group.serializeElement(
                    P256Group.scalarMultiplyGenerator(P256Group.decodeScalar(Oprf.fromHex(KEY_A)))))).substring(0, 16);

            assertThat(keyRing.activeKeyId()).isEqualTo(expectedKeyId);
            assertThat(keyRing.allKeys()).hasSize(1);
        });
    }

    @Test
    @DisplayName("retains superseded keys so a rotation can overlap")
    void retainsPreviousKeys() {
        runner.withPropertyValues(
                "application.security.oprf.active-key=" + KEY_A,
                "application.security.oprf.previous-keys=" + KEY_B
        ).run(context -> {
            OprfKeyRing keyRing = context.getBean(OprfKeyRing.class);

            String previousKeyId = keyRing.allKeys().stream()
                    .map(key -> key.keyId())
                    .filter(id -> !id.equals(keyRing.activeKeyId()))
                    .findFirst()
                    .orElseThrow();

            assertThat(keyRing.allKeys()).hasSize(2);
            assertThat(keyRing.isKnownKeyId(previousKeyId)).isTrue();
            assertThat(keyRing.findByKeyId(previousKeyId)).isPresent();
            assertThat(keyRing.findByKeyId("ffffffffffffffff")).isEmpty();

            // An unnamed key id resolves to the active key, which is what makes
            // keyId optional on the evaluate request.
            assertThat(keyRing.findByKeyId(null)).contains(keyRing.activeKey());
        });
    }

    @Test
    @DisplayName("rejects a malformed key rather than silently truncating it")
    void rejectsMalformedKey() {
        runner.withPropertyValues("application.security.oprf.active-key=not-a-scalar")
                .run(context -> assertThat(context).hasFailed());

        runner.withPropertyValues("application.security.oprf.active-key=" + "00".repeat(32))
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    @DisplayName("the dev profile boots on the public development key")
    void devProfileUsesDevelopmentKey() {
        runner.withPropertyValues("spring.profiles.active=dev").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(OprfKeyRing.class).activeKeyId()).isNotBlank();
        });
    }

    private static String rootCauseMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage();
    }
}
