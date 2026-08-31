package teknofest.signa.producer.security;

import java.util.UUID;

/**
 * The authenticated identity of a member bank calling the machine-to-machine
 * API. Carries the bank id so that every OPRF evaluation can be rate limited
 * and audited against the institution that asked for it.
 */
public record BankPrincipal(UUID bankId, String clientId, String bankName) {

    @Override
    public String toString() {
        return "BankPrincipal[clientId=" + clientId + "]";
    }
}
