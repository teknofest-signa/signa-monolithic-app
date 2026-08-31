package teknofest.signa.producer.model.event;

/**
 * A block propagating across the network, carrying the OPRF pseudonym rather
 * than the raw identifier it replaced. Consumers can match it against their own
 * enrolments without learning who it refers to.
 */
public record BlockEvent(String pseudonym, String oprfKeyId) {
}
