package teknofest.signa.producer.service;

import static teknofest.signa.producer.constants.ErrorConstants.UNKNOWN_OPRF_KEY;

import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import teknofest.signa.producer.crypto.OprfProtocolException;
import teknofest.signa.producer.enums.CustomerStatus;
import teknofest.signa.producer.enums.ScreeningStatus;
import teknofest.signa.producer.handler.exception.ApplicationException;
import teknofest.signa.producer.model.dto.screening.ScreeningRequest;
import teknofest.signa.producer.model.dto.screening.ScreeningResponse;
import teknofest.signa.producer.model.entity.ScreeningCheck;
import teknofest.signa.producer.repository.CustomerRepository;
import teknofest.signa.producer.repository.ScreeningCheckRepository;
import teknofest.signa.producer.security.BankPrincipal;

/**
 * The question the platform exists to answer: is the person behind this
 * pseudonym flagged anywhere in the network?
 *
 * <p>This is the second half of the exchange, and it is a separate call for a
 * reason worth stating plainly, because the obvious design does not work. The
 * evaluation endpoint receives {@code B = r·P} and returns {@code k·B}. It
 * cannot remove the bank's blind — {@code r} is fresh per request and never
 * leaves the bank — so at that moment the server holds no pseudonym and has
 * nothing to match against. Only the bank can unblind. So the bank unblinds,
 * and comes back here with {@code k·P}, which the server can compare but
 * cannot invert.
 *
 * <p>What this service is careful about is the shape of the answer. Matching
 * happens here, server-side, and what goes back is a verdict rather than the
 * records behind it. A response naming the institution that flagged the person
 * would let a caller assemble another bank's fraud list one query at a time,
 * which would cost more privacy than the OPRF buys.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScreeningService {

    /**
     * BLOCKED is a decision some institution took. SUSPENDED is this platform
     * propagating that decision to the same person's other enrolments. Either
     * means the network is carrying an adverse signal, so both count. ACTIVE
     * and INACTIVE do not: INACTIVE is a dormant relationship, not a judgement
     * about the person.
     */
    private static final List<CustomerStatus> ADVERSE_STATUSES =
            List.of(CustomerStatus.BLOCKED, CustomerStatus.SUSPENDED);

    private final OprfService oprfService;
    private final OprfRateLimiter rateLimiter;
    private final CustomerRepository customerRepository;
    private final ScreeningCheckRepository screeningCheckRepository;

    public ScreeningResponse screen(BankPrincipal bank, ScreeningRequest request) {
        String keyId = request.getOprfKeyId();

        // A pseudonym derived under a key this server does not hold cannot
        // match anything, so the honest answer is an error rather than CLEAR.
        // Reporting it as CLEAR would be the worst failure available here: a
        // caller misconfigured against a retired key would wave through every
        // flagged customer in the network and see nothing wrong.
        if (!oprfService.isKnownKeyId(keyId)) {
            recordAudit(bank, keyId, false, null, "unknown key id");
            throw new ApplicationException(UNKNOWN_OPRF_KEY);
        }

        OprfRateLimiter.Decision decision = rateLimiter.tryConsumeScreening(bank.bankId(), 1);
        if (!decision.allowed()) {
            recordAudit(bank, keyId, false, null, decision.reason());
            throw new OprfProtocolException("Screening quota exceeded: " + decision.reason());
        }

        boolean flagged = customerRepository.existsByPseudonymAndOprfKeyIdAndCustomerStatusIn(
                request.getPseudonym(), keyId, ADVERSE_STATUSES);

        // The caller's own enrolment. Reading it back to them discloses
        // nothing they did not already have, and it saves a second query.
        boolean enrolledWithYou = customerRepository.existsByBankIdAndPseudonymAndOprfKeyId(
                bank.bankId(), request.getPseudonym(), keyId);

        recordAudit(bank, keyId, true, flagged, null);

        // Verdicts and counts only. Logging the pseudonym would put a
        // cross-bank identifier into the log pipeline, which is the one place
        // it is hardest to erase.
        log.info("Screening check for bank {} under key {}: {}",
                bank.bankId(), keyId, flagged ? "FLAGGED" : "CLEAR");

        return ScreeningResponse.builder()
                .status(flagged ? ScreeningStatus.FLAGGED : ScreeningStatus.CLEAR)
                .oprfKeyId(keyId)
                .enrolledWithYou(enrolledWithYou)
                .checkedAt(Instant.now())
                .build();
    }

    /**
     * Records who asked, when, under which key, and what they were told —
     * never the pseudonym.
     *
     * <p>Deliberately not inside a transaction the caller can roll back.
     * Refusals are the records most worth keeping, and a bank grinding against
     * its quota would erase its own audit trail if the write were undone by
     * the exception that follows it.
     */
    private void recordAudit(BankPrincipal bank, String keyId, boolean accepted, Boolean flagged, String reason) {
        try {
            screeningCheckRepository.save(ScreeningCheck.builder()
                    .bankId(bank.bankId())
                    .keyId(keyId)
                    .accepted(accepted)
                    .flagged(flagged)
                    .rejectionReason(reason)
                    .build());
        } catch (RuntimeException exception) {
            log.error("Failed to write screening audit record for bank {}", bank.bankId(), exception);
        }
    }
}
