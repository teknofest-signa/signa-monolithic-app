package teknofest.signa.producer.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import teknofest.signa.producer.enums.Role;
import teknofest.signa.producer.enums.Status;
import teknofest.signa.producer.model.entity.Admin;
import teknofest.signa.producer.repository.AdminRepository;

/**
 * Creates the first super administrator on a development machine.
 *
 * <p>Without this the service cannot be signed into at all: an operator account
 * is only created by {@code SuperAdminService.createAdmin}, which hardcodes the
 * ADMIN role and is itself restricted to a SUPER_ADMIN, and the self-registration
 * path needs an invite token that only that method issues. A fresh database
 * therefore has no way in.
 *
 * <p>Restricted to the {@code dev} profile. The password is written to the log
 * on purpose, which is exactly why this must never run anywhere real: in
 * production the first operator belongs in a seeded migration or a one-off
 * provisioning command, with a password nobody has printed.
 */
@Slf4j
@Configuration
@Profile("dev")
@RequiredArgsConstructor
public class DevBootstrap {

    @Value("${application.bootstrap.super-admin.email:admin@signa.az}")
    private String email;

    @Value("${application.bootstrap.super-admin.username:Signa Operator}")
    private String username;

    @Value("${application.bootstrap.super-admin.password:signa-dev-password}")
    private String password;

    @Bean
    public ApplicationRunner seedSuperAdmin(AdminRepository adminRepository, PasswordEncoder passwordEncoder) {
        return _ -> {
            if (adminRepository.existsByEmail(email)) {
                log.info("Development super administrator already present: {}", email);
                return;
            }

            adminRepository.save(Admin.builder()
                    .email(email)
                    .username(username)
                    .password(passwordEncoder.encode(password))
                    .role(Role.SUPER_ADMIN)
                    .status(Status.ACTIVE)
                    .build());

            log.warn("""

                    ============================================================
                     Seeded a DEVELOPMENT super administrator.

                       email    {}
                       password {}

                     This account exists only under the 'dev' profile and its
                     password has just been written to this log. Do not carry
                     it into any environment holding real data.
                    ============================================================
                    """, email, password);
        };
    }
}
