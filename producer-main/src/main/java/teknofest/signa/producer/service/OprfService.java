package teknofest.signa.producer.service;

import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.math.ec.ECPoint;
import org.springframework.stereotype.Service;
import teknofest.signa.producer.config.OprfProperties;
import teknofest.signa.producer.crypto.Oprf;
import teknofest.signa.producer.crypto.OprfProtocolException;
import teknofest.signa.producer.crypto.P256Group;
import teknofest.signa.producer.crypto.VoprfServer;
import teknofest.signa.producer.crypto.key.OprfKey;
import teknofest.signa.producer.crypto.key.OprfKeyRing;
import teknofest.signa.producer.model.dto.oprf.OprfEvaluateRequest;
import teknofest.signa.producer.model.dto.oprf.OprfEvaluateResponse;
import teknofest.signa.producer.model.dto.oprf.OprfPublicKeyResponse;
import teknofest.signa.producer.model.entity.OprfEvaluation;
import teknofest.signa.producer.repository.OprfEvaluationRepository;
import teknofest.signa.producer.security.BankPrincipal;

/**
 * The server side of the SIGNA privacy layer.
 *
 * <p>This service performs the one operation the central server is trusted to
 * perform on customer identity: multiplying a blinded curve point by the server
 * key, and proving it used the right key. It cannot learn who was asked about,
 * and there is no method here that accepts an identifier.
 *
 * <p>Its responsibilities beyond the arithmetic are the ones that make the
 * arithmetic worth anything: validate every point before it touches the key,
 * bound how much any one bank may evaluate, and leave an audit trail.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OprfService {

    private final OprfKeyRing keyRing;
    private final OprfProperties properties;
    private final OprfRateLimiter rateLimiter;
    private final OprfEvaluationRepository oprfEvaluationRepository;

    /**
     * BlindEvaluate over a batch, on behalf of an authenticated member bank.
     */
    public OprfEvaluateResponse evaluate(BankPrincipal bank, OprfEvaluateRequest request) {
        List<String> encodedElements = request.getBlindedElements();

        if (encodedElements.size() > properties.getMaxBatchSize()) {
            throw new OprfProtocolException(
                    "Batch exceeds the maximum of " + properties.getMaxBatchSize() + " elements");
        }

        OprfKey key = keyRing.findByKeyId(request.getKeyId())
                .orElseThrow(() -> new OprfProtocolException("Unknown key id"));

        OprfRateLimiter.Decision decision = rateLimiter.tryConsume(bank.bankId(), encodedElements.size());
        if (!decision.allowed()) {
            recordAudit(bank, key, encodedElements.size(), false, decision.reason());
            throw new OprfProtocolException("OPRF quota exceeded: " + decision.reason());
        }

        // Decode and validate before any secret-key arithmetic. A point that is
        // off the curve or in a small subgroup can leak the key one residue at
        // a time if it is ever multiplied, so nothing unvalidated gets that far.
        List<ECPoint> blindedElements = new ArrayList<>(encodedElements.size());
        for (String encoded : encodedElements) {
            blindedElements.add(P256Group.decodeElement(Oprf.fromHex(encoded)));
        }

        VoprfServer.BlindEvaluateResult result =
                VoprfServer.blindEvaluate(key.secretKey(), key.publicKey(), blindedElements);

        List<String> evaluatedElements = new ArrayList<>(result.evaluatedElements().size());
        for (ECPoint evaluatedElement : result.evaluatedElements()) {
            evaluatedElements.add(Oprf.toHex(P256Group.serializeElement(evaluatedElement)));
        }

        recordAudit(bank, key, encodedElements.size(), true, null);
        log.info("OPRF evaluation for bank {}: {} element(s) under key {}",
                bank.bankId(), encodedElements.size(), key.keyId());

        return OprfEvaluateResponse.builder()
                .evaluatedElements(evaluatedElements)
                .proof(result.proof().toHex())
                .keyId(key.keyId())
                .publicKey(key.publicKeyHex())
                .ciphersuite(Oprf.CIPHERSUITE_IDENTIFIER)
                .build();
    }

    public OprfPublicKeyResponse publicParameters() {
        List<OprfPublicKeyResponse.KeyInfo> keys = keyRing.allKeys().stream()
                .map(key -> OprfPublicKeyResponse.KeyInfo.builder()
                        .keyId(key.keyId())
                        .publicKey(key.publicKeyHex())
                        .active(key.keyId().equals(keyRing.activeKeyId()))
                        .build())
                .toList();

        return OprfPublicKeyResponse.builder()
                .ciphersuite(Oprf.CIPHERSUITE_IDENTIFIER)
                .mode("VOPRF")
                .activeKeyId(keyRing.activeKeyId())
                .activePublicKey(keyRing.activeKey().publicKeyHex())
                .keys(keys)
                .build();
    }

    public boolean isKnownKeyId(String keyId) {
        return keyRing.isKnownKeyId(keyId);
    }

    public String activeKeyId() {
        return keyRing.activeKeyId();
    }

    /**
     * Records who evaluated how much, including refusals — a bank repeatedly
     * hitting its ceiling is exactly the signal worth keeping.
     *
     * <p>Audit failures never fail the request, but they are logged at error
     * level so a silently broken trail does not go unnoticed.
     */
    private void recordAudit(BankPrincipal bank, OprfKey key, int batchSize, boolean accepted, String reason) {
        try {
            oprfEvaluationRepository.save(OprfEvaluation.builder()
                    .bankId(bank.bankId())
                    .keyId(key.keyId())
                    .batchSize(batchSize)
                    .accepted(accepted)
                    .rejectionReason(reason)
                    .build());
        } catch (RuntimeException exception) {
            log.error("Failed to write OPRF audit record for bank {}", bank.bankId(), exception);
        }
    }
}
