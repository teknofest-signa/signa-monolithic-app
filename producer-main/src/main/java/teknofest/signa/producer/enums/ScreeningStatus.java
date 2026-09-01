package teknofest.signa.producer.enums;

/**
 * The answer a member bank gets back from a screening check.
 *
 * <p>Two values, deliberately. A third value distinguishing "not known to the
 * network" from "known and in good standing" would tell the caller whether a
 * person banks anywhere else, which is a fact about that person that the
 * platform has no business disclosing. Both collapse into {@link #CLEAR}.
 */
public enum ScreeningStatus {

    /** No enrolment of this person anywhere in the network is blocked or suspended. */
    CLEAR,

    /**
     * At least one member institution has blocked this person, or the network
     * suspended them because another institution did. Which institution, and
     * under what name, is not disclosed.
     */
    FLAGGED
}
