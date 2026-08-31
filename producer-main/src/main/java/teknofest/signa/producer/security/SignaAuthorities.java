package teknofest.signa.producer.security;

/**
 * Authority names used in the security configuration.
 *
 * <p>{@link #BANK} is deliberately not a value of
 * {@link teknofest.signa.producer.enums.Role}. Role describes a human operator
 * of the back office; BANK describes a member institution authenticating with a
 * machine credential. Keeping them in separate namespaces means no admin
 * account can ever be provisioned into the authority that grants OPRF access.
 */
public final class SignaAuthorities {

    private SignaAuthorities() {
    }

    public static final String SUPER_ADMIN = "SUPER_ADMIN";
    public static final String ADMIN = "ADMIN";
    public static final String BANK = "BANK";
}
