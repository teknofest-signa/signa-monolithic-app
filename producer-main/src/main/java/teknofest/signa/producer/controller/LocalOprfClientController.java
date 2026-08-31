package teknofest.signa.producer.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import teknofest.signa.producer.model.dto.oprf.LocalDeriveRequest;
import teknofest.signa.producer.service.LocalOprfClientService;

/**
 * Development-only endpoint that derives a pseudonym from a raw identifier by
 * running both halves of the protocol on the server.
 *
 * <p>It exists so the back office can be demonstrated before the bank
 * connectors are written. Because the identifier reaches the server, it is the
 * one part of this layer that does not hold the privacy property, which is why
 * it is compiled out unless explicitly enabled, restricted to SUPER_ADMIN, and
 * refuses to start in production.
 */
@Tag(name = "OPRF (development only)",
        description = "Local reference client. Accepts raw identifiers; disabled by default and never for production.")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/oprf/local")
@ConditionalOnProperty(prefix = "application.security.oprf.local-client", name = "enabled", havingValue = "true")
public class LocalOprfClientController {

    private final LocalOprfClientService localOprfClientService;

    @Operation(summary = "Derive a pseudonym from a raw identifier (development only)",
            description = "Runs blind, evaluate, verify and unblind in-process. The identifier is not stored "
                    + "or logged. Production deployments derive pseudonyms inside the bank instead.")
    @PostMapping("/derive")
    @PreAuthorize("hasAuthority('SUPER_ADMIN')")
    public LocalOprfClientService.Result derive(@Valid @RequestBody LocalDeriveRequest request) {
        return localOprfClientService.derivePseudonym(request.getIdentifierType(), request.getIdentifier());
    }
}
