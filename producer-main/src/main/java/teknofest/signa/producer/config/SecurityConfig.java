package teknofest.signa.producer.config;

import static org.springframework.security.config.Customizer.withDefaults;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import teknofest.signa.producer.security.BankApiKeyAuthFilter;
import teknofest.signa.producer.security.JwtAuthFilter;
import teknofest.signa.producer.security.JwtService;
import teknofest.signa.producer.security.SignaAuthorities;
import teknofest.signa.producer.service.BankApiKeyService;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private static final String[] SWAGGER_WHITELIST = {
            "/swagger-ui/**",
            "/v3/api-docs/**",
            "/swagger-resources/**",
            "/swagger-resources"
    };

    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;
    private final BankApiKeyService bankApiKeyService;

    @Value("${application.security.cors.allowed-origins}")
    private List<String> allowedOrigins;

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration corsConfiguration = new CorsConfiguration();

        // Origins come from configuration rather than a hard-coded list, so a
        // new deployment does not require a code change to be reachable, and so
        // a staging origin cannot be left behind in a production build.
        corsConfiguration.setAllowedOrigins(allowedOrigins);
        corsConfiguration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        corsConfiguration.setAllowedHeaders(List.of(
                "Authorization",
                "Content-Type",
                "x-auth-token",
                BankApiKeyAuthFilter.CLIENT_ID_HEADER,
                BankApiKeyAuthFilter.API_KEY_HEADER
        ));
        corsConfiguration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource urlBasedCorsConfigurationSource = new UrlBasedCorsConfigurationSource();
        urlBasedCorsConfigurationSource.registerCorsConfiguration("/**", corsConfiguration);

        return urlBasedCorsConfigurationSource;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity httpSecurity) {
        httpSecurity
                .cors(withDefaults())
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(SWAGGER_WHITELIST).permitAll()
                        .requestMatchers("/api/v1/auth/**").permitAll()
                        .requestMatchers("/api/v1/oprf/public-key").permitAll()
                        // Ordered before the BANK rule below: the development
                        // client is operated by a human, not by a member bank,
                        // so a BANK-only rule here would make it unreachable.
                        .requestMatchers("/api/v1/oprf/local/**").hasAuthority(SignaAuthorities.SUPER_ADMIN)
                        .requestMatchers("/api/v1/oprf/**").hasAuthority(SignaAuthorities.BANK)
                        // Screening is a separate authority decision from
                        // evaluation even though both are member-bank calls:
                        // one hands out blinded arithmetic, the other hands
                        // out a fact about a person. Naming it explicitly
                        // keeps it from ever inheriting a rule written for
                        // something else.
                        .requestMatchers("/api/v1/screening/**").hasAuthority(SignaAuthorities.BANK)
                        .requestMatchers("/api/v1/super-admins/**").hasAuthority(SignaAuthorities.SUPER_ADMIN)
                        .requestMatchers("/api/v1/backoffice/**").hasAnyAuthority(SignaAuthorities.SUPER_ADMIN, SignaAuthorities.ADMIN)
                        .requestMatchers("/api/v1/banks/**").hasAnyAuthority(SignaAuthorities.SUPER_ADMIN, SignaAuthorities.ADMIN)
                        .requestMatchers("/api/v1/simulation/**").hasAnyAuthority(SignaAuthorities.SUPER_ADMIN, SignaAuthorities.ADMIN)
                        .requestMatchers("/api/v1/customers/**").hasAnyAuthority(SignaAuthorities.SUPER_ADMIN, SignaAuthorities.ADMIN)
                        // Deny by default. Without this, any path not named
                        // above is reachable unauthenticated, so every new
                        // controller ships open until someone remembers to add
                        // a rule for it.
                        .anyRequest().authenticated()
                )
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterBefore(new JwtAuthFilter(jwtService, userDetailsService), UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(new BankApiKeyAuthFilter(bankApiKeyService), UsernamePasswordAuthenticationFilter.class);

        return httpSecurity.build();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authenticationConfiguration) {
        return authenticationConfiguration.getAuthenticationManager();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
