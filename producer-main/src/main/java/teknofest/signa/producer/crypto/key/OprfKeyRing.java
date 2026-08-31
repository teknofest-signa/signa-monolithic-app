package teknofest.signa.producer.crypto.key;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The set of OPRF keys this server will evaluate under: exactly one active key,
 * plus any number of previous keys retained for migration.
 *
 * <p>Rotating k changes every pseudonym it produces, so a rotation is not a
 * silent operation — it is a re-enrolment of the entire federation. Keeping the
 * previous keys evaluable is what makes a rotation possible at all: during the
 * overlap a bank can still compute an old pseudonym to locate an existing
 * record while enrolling the new one alongside it. See
 * {@code docs/oprf-layer.md} for the migration procedure.
 */
public final class OprfKeyRing {

    private final Map<String, OprfKey> keysById;
    private final OprfKey activeKey;

    public OprfKeyRing(OprfKey activeKey, List<OprfKey> previousKeys) {
        this.activeKey = activeKey;

        Map<String, OprfKey> keys = new LinkedHashMap<>();
        keys.put(activeKey.keyId(), activeKey);
        for (OprfKey previousKey : previousKeys) {
            // A repeated key id means the same key was listed twice, since ids
            // are derived from the key itself. Keep the active entry.
            keys.putIfAbsent(previousKey.keyId(), previousKey);
        }
        this.keysById = Map.copyOf(keys);
    }

    public OprfKey activeKey() {
        return activeKey;
    }

    public String activeKeyId() {
        return activeKey.keyId();
    }

    /**
     * Looks up a key by identifier, defaulting to the active key when the
     * caller does not name one.
     */
    public Optional<OprfKey> findByKeyId(String keyId) {
        if (keyId == null || keyId.isBlank()) {
            return Optional.of(activeKey);
        }
        return Optional.ofNullable(keysById.get(keyId));
    }

    /** True when pseudonyms tagged with this id can still be matched. */
    public boolean isKnownKeyId(String keyId) {
        return keyId != null && keysById.containsKey(keyId);
    }

    public Collection<OprfKey> allKeys() {
        return keysById.values();
    }
}
