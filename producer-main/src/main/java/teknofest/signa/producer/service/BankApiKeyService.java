package teknofest.signa.producer.service;

import static teknofest.signa.producer.constants.ErrorConstants.BANK_NOT_FOUND;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import teknofest.signa.producer.crypto.Oprf;
import teknofest.signa.producer.handler.exception.ResourceNotFoundException;
import teknofest.signa.producer.model.entity.Bank;
import teknofest.signa.producer.repository.BankRepository;

/**
 * Issues and verifies the machine credentials a member bank uses to reach the
 * OPRF endpoint.
 *
 * <p>The key itself is never stored. Only a SHA-256 digest of it is kept, so a
 * database disclosure does not hand an attacker the ability to call the OPRF as
 * a bank. A plain digest rather than bcrypt is the right choice here precisely
 * because the key is machine generated with 256 bits of entropy: there is no
 * dictionary to slow down, and bcrypt on a hot path that runs once per
 * enrolment request would only add latency.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BankApiKeyService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final int API_KEY_BYTES = 32;
    private static final int CLIENT_ID_BYTES = 8;
    private static final String CLIENT_ID_PREFIX = "bank_";
    private static final String API_KEY_PREFIX = "signa_sk_";

    /**
     * Digest of a value that is not a valid key, compared against whenever the
     * client id is unknown so that a caller cannot tell "no such bank" from
     * "wrong key" by timing the response.
     */
    private static final String DECOY_DIGEST = sha256Hex("signa-decoy-credential");

    private final BankRepository bankRepository;

    public String generateClientId() {
        byte[] random = new byte[CLIENT_ID_BYTES];
        SECURE_RANDOM.nextBytes(random);
        return CLIENT_ID_PREFIX + Oprf.toHex(random);
    }

    /**
     * Mints a new key for a bank and stores only its digest.
     *
     * @return the plaintext key, which the caller must hand to the bank now and
     *         then discard; it cannot be recovered afterwards
     */
    @Transactional
    public String issueApiKey(Bank bank) {
        byte[] random = new byte[API_KEY_BYTES];
        SECURE_RANDOM.nextBytes(random);
        String apiKey = API_KEY_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(random);

        bank.setApiKeyHash(sha256Hex(apiKey));
        bank.setApiKeyRotatedAt(Instant.now());
        bankRepository.save(bank);

        return apiKey;
    }

    @Transactional
    public String rotateApiKey(UUID bankId) {
        Bank bank = bankRepository.findById(bankId)
                .orElseThrow(() -> new ResourceNotFoundException(BANK_NOT_FOUND));

        String apiKey = issueApiKey(bank);
        log.info("OPRF API key rotated for bank {}", bankId);

        return apiKey;
    }

    /**
     * Resolves a client id and presented key to a bank, or returns empty.
     *
     * <p>Runs the digest comparison on every path, including the unknown-client
     * path, and reports no reason for the failure.
     */
    @Transactional(readOnly = true)
    public Optional<Bank> authenticate(String clientId, String presentedApiKey) {
        if (clientId == null || presentedApiKey == null) {
            return Optional.empty();
        }

        Optional<Bank> candidate = bankRepository.findByClientId(clientId);
        String storedDigest = candidate
                .map(Bank::getApiKeyHash)
                .orElse(DECOY_DIGEST);

        boolean matches = Oprf.constantTimeEquals(
                Oprf.ascii(storedDigest == null ? DECOY_DIGEST : storedDigest),
                Oprf.ascii(sha256Hex(presentedApiKey))
        );

        if (!matches || candidate.isEmpty()) {
            return Optional.empty();
        }
        return candidate;
    }

    private static String sha256Hex(String value) {
        return Oprf.toHex(Oprf.hash(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }
}
