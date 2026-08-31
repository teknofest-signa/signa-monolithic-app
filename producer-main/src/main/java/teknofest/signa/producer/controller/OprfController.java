package teknofest.signa.producer.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import teknofest.signa.producer.handler.exception.InvalidCredentialsException;
import teknofest.signa.producer.model.dto.oprf.OprfEvaluateRequest;
import teknofest.signa.producer.model.dto.oprf.OprfEvaluateResponse;
import teknofest.signa.producer.model.dto.oprf.OprfPublicKeyResponse;
import teknofest.signa.producer.security.BankPrincipal;
import teknofest.signa.producer.security.SignaAuthorities;
import teknofest.signa.producer.service.OprfService;

/**
 * The oblivious evaluation endpoint.
 *
 * <p>This is the only part of the platform that touches customer identity, and
 * it touches it in a form it cannot read. A member bank sends curve points; it
 * receives curve points and a proof. No request or response on this controller
 * has a field capable of holding a name, a FIN, or any other identifier.
 */
@Tag(name = "OPRF", description = "Oblivious pseudonym derivation for member banks (RFC 9497 VOPRF, P256-SHA256)")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/oprf")
public class OprfController {

    private final OprfService oprfService;

    @Operation(summary = "Publish the OPRF public parameters",
            description = "Returns the active key id and public key so a bank can pin the key it expects "
                    + "and verify the DLEQ proofs returned by evaluation.")
    @GetMapping("/public-key")
    public OprfPublicKeyResponse publicKey() {
        return oprfService.publicParameters();
    }

    @Operation(summary = "Evaluate a batch of blinded elements",
            description = "Multiplies each blinded element by the server key and returns a batched DLEQ proof "
                    + "that the advertised key was used. Requires member-bank credentials "
                    + "(X-Signa-Client-Id and X-Signa-Api-Key) and is subject to per-bank quotas.")
    @PostMapping("/evaluate")
    @PreAuthorize("hasAuthority('" + SignaAuthorities.BANK + "')")
    public OprfEvaluateResponse evaluate(@Valid @RequestBody OprfEvaluateRequest request,
                                         Authentication authentication) {
        return oprfService.evaluate(requireBank(authentication), request);
    }

    private BankPrincipal requireBank(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof BankPrincipal bankPrincipal)) {
            throw new InvalidCredentialsException("Member bank credentials are required");
        }
        return bankPrincipal;
    }
}
