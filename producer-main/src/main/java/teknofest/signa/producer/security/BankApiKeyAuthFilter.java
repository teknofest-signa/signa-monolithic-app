package teknofest.signa.producer.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;
import teknofest.signa.producer.model.entity.Bank;
import teknofest.signa.producer.service.BankApiKeyService;

/**
 * Authenticates member banks on the machine-to-machine API using a client id
 * and API key pair.
 *
 * <p>Banks are not back-office users and must not borrow an operator's session:
 * an admin JWT carries no bank identity, so an OPRF call made under one could
 * be neither rate limited nor attributed. This filter is the only way to obtain
 * the {@code BANK} authority.
 */
@Slf4j
@RequiredArgsConstructor
public class BankApiKeyAuthFilter extends OncePerRequestFilter {

    public static final String CLIENT_ID_HEADER = "X-Signa-Client-Id";
    public static final String API_KEY_HEADER = "X-Signa-Api-Key";

    private final BankApiKeyService bankApiKeyService;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        String clientId = request.getHeader(CLIENT_ID_HEADER);
        String apiKey = request.getHeader(API_KEY_HEADER);

        if (clientId == null || apiKey == null || SecurityContextHolder.getContext().getAuthentication() != null) {
            filterChain.doFilter(request, response);
            return;
        }

        Optional<Bank> bank = bankApiKeyService.authenticate(clientId, apiKey);
        if (bank.isEmpty()) {
            // Left unauthenticated on purpose. The authorisation rules will
            // reject the request, and no detail about which half of the
            // credential was wrong reaches the caller.
            log.warn("Rejected bank API credentials for clientId={}", clientId);
            filterChain.doFilter(request, response);
            return;
        }

        Bank authenticatedBank = bank.get();
        if (!authenticatedBank.isOprfEnabled()) {
            log.warn("Bank {} presented valid credentials but OPRF access is disabled", authenticatedBank.getId());
            filterChain.doFilter(request, response);
            return;
        }

        BankPrincipal principal = new BankPrincipal(
                authenticatedBank.getId(), authenticatedBank.getClientId(), authenticatedBank.getName());

        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority(SignaAuthorities.BANK)));
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authentication);

        filterChain.doFilter(request, response);
    }
}
