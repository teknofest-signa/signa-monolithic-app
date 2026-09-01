package teknofest.signa.producer.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import teknofest.signa.producer.handler.exception.InvalidCredentialsException;
import teknofest.signa.producer.model.dto.screening.ScreeningRequest;
import teknofest.signa.producer.model.dto.screening.ScreeningResponse;
import teknofest.signa.producer.security.BankPrincipal;
import teknofest.signa.producer.security.SignaAuthorities;
import teknofest.signa.producer.service.ScreeningService;

/**
 * The cross-institution check, on a pseudonym rather than on a person.
 *
 * <p>Together with {@link OprfController} this is the whole of the shared
 * intelligence: evaluate to obtain the pseudonym without disclosing the
 * identifier, then screen the pseudonym to learn whether the network carries
 * an adverse signal for that person. Neither call has a field capable of
 * holding a name or a FIN.
 */
@Tag(name = "Screening",
        description = "Cross-institution checks on an OPRF pseudonym, for member banks")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/screening")
public class ScreeningController {

    private final ScreeningService screeningService;

    /**
     * POST rather than GET with the pseudonym in the path.
     *
     * <p>A pseudonym is a stable cross-bank identifier for a natural person.
     * In a URL it would be copied into the access log of every proxy on the
     * path, into browser history and into any referrer header — none of which
     * this platform can promise to erase. A body is not secret either, but it
     * is not routinely recorded by infrastructure that has no idea what it is
     * carrying.
     */
    @Operation(summary = "Check a pseudonym against the network",
            description = "Answers CLEAR or FLAGGED for the person behind the pseudonym, without naming the "
                    + "institution that flagged them. Requires member-bank credentials "
                    + "(X-Signa-Client-Id and X-Signa-Api-Key) and is subject to per-bank quotas.")
    @PostMapping
    @PreAuthorize("hasAuthority('" + SignaAuthorities.BANK + "')")
    public ScreeningResponse screen(@Valid @RequestBody ScreeningRequest request,
                                    Authentication authentication) {
        return screeningService.screen(requireBank(authentication), request);
    }

    private BankPrincipal requireBank(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof BankPrincipal bankPrincipal)) {
            throw new InvalidCredentialsException("Member bank credentials are required");
        }
        return bankPrincipal;
    }
}
