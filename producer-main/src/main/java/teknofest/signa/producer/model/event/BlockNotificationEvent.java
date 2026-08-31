package teknofest.signa.producer.model.event;

/**
 * Notification that a pseudonym was blocked somewhere in the network. Scoped by
 * key id because pseudonyms are only comparable within one OPRF key.
 */
public record BlockNotificationEvent(String pseudonym, String oprfKeyId) {
}
