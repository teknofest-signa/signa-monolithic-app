package teknofest.signa.producer.service;

import static org.springframework.security.core.userdetails.User.withUsername;
import static teknofest.signa.producer.constants.ErrorConstants.*;
import static teknofest.signa.producer.constants.TimeConstants.FORGOT_PASSWORD_COOLDOWN_SECONDS;
import static teknofest.signa.producer.constants.TimeConstants.PASSWORD_RESET_TOKEN_EXPIRY_MINUTES;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import teknofest.signa.producer.enums.NotificationType;
import teknofest.signa.producer.handler.exception.ApplicationException;
import teknofest.signa.producer.model.dto.auth.AuthResponse;
import teknofest.signa.producer.model.dto.auth.ForgotPasswordRequest;
import teknofest.signa.producer.model.dto.auth.LoginRequest;
import teknofest.signa.producer.model.dto.auth.RegisterRequest;
import teknofest.signa.producer.model.dto.auth.ResetPasswordRequest;
import teknofest.signa.producer.model.entity.Admin;
import teknofest.signa.producer.enums.Status;
import teknofest.signa.producer.handler.exception.ResourceNotFoundException;
import teknofest.signa.producer.model.event.NotificationEvent;
import teknofest.signa.producer.repository.AdminRepository;
import teknofest.signa.producer.security.JwtService;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final JwtService jwtService;
    private final AdminRepository adminRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final ApplicationEventPublisher applicationEventPublisher;

    @Value("${application.frontend.base-url}")
    private String frontendBaseUrl;

    public AuthResponse login(LoginRequest loginRequest) {
        String email = loginRequest.getEmail();
        log.info("login started for user: {}", email);

        Admin admin = adminRepository.findByEmailAndStatus(email, Status.ACTIVE)
                .orElseThrow(() -> new ResourceNotFoundException(ADMIN_NOT_FOUND));

        authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(email, loginRequest.getPassword()));

        return generateAuthResponse(admin);
    }

    public AuthResponse register(RegisterRequest registerRequest, String token) {
        Admin admin = adminRepository.findByTokenAndStatus(token, Status.PENDING)
                .orElseThrow(() -> new ResourceNotFoundException(TOKEN_NOT_FOUND));

        if (adminRepository.existsByUsername(registerRequest.getUsername())) {
            throw new ApplicationException(USERNAME_ALREADY_EXISTS);
        }

        admin.setToken(null);
        admin.setPassword(passwordEncoder.encode(registerRequest.getPassword()));
        admin.setUsername(registerRequest.getUsername());
        admin.setStatus(Status.ACTIVE);

        adminRepository.save(admin);

        return generateAuthResponse(admin);
    }

    public void forgotPassword(ForgotPasswordRequest forgotPasswordRequest) {
        String email = forgotPasswordRequest.getEmail();
        log.info("forgot password started for user: {}", email);

        Admin admin = adminRepository.findByEmailAndStatus(email, Status.ACTIVE)
                .orElseThrow(() -> new ResourceNotFoundException(ADMIN_NOT_FOUND));

        if (Objects.nonNull(admin.getResetPasswordTokenCreatedAt())) {
            long secondsSinceLastRequest = Instant.now().getEpochSecond() - admin.getResetPasswordTokenCreatedAt().getEpochSecond();

            if (secondsSinceLastRequest < FORGOT_PASSWORD_COOLDOWN_SECONDS) {
                throw new ApplicationException(PASSWORD_COOLDOWN_EXCEPTION);
            }
        }

        String token = UUID.randomUUID().toString();
        admin.setResetPasswordToken(token);
        admin.setResetPasswordTokenCreatedAt(Instant.now());

        adminRepository.save(admin);

        applicationEventPublisher.publishEvent(new NotificationEvent(
                admin.getEmail(),
                NotificationType.PASSWORD_RESET,
                Map.of(
                        "username", admin.getUsername(),
                        "resetUrl", frontendBaseUrl + "/reset-password?token=" + token
                )
        ));
    }

    public void resetPassword(ResetPasswordRequest resetPasswordRequest) {
        Admin admin = adminRepository.findByResetPasswordToken(resetPasswordRequest.getToken())
                .orElseThrow(() -> new ResourceNotFoundException(TOKEN_NOT_FOUND));

        long minutesSinceIssued = Duration.between(admin.getResetPasswordTokenCreatedAt(), Instant.now()).toMinutes();

        if (minutesSinceIssued >= PASSWORD_RESET_TOKEN_EXPIRY_MINUTES) {
            admin.setResetPasswordToken(null);
            admin.setResetPasswordTokenCreatedAt(null);

            adminRepository.save(admin);

            throw new ApplicationException(PASSWORD_EXPIRED);
        }

        admin.setPassword(passwordEncoder.encode(resetPasswordRequest.getPassword()));
        admin.setResetPasswordToken(null);
        admin.setResetPasswordTokenCreatedAt(null);

        adminRepository.save(admin);

        log.info("password reset successfully for user: {}", admin.getEmail());
    }

    private AuthResponse generateAuthResponse(Admin admin) {
        var UserDetails = withUsername(admin.getEmail())
                .password(admin.getPassword())
                .authorities(admin.getRole().name())
                .build();

        return AuthResponse.builder()
                .token(jwtService.generateToken(UserDetails))
                .build();
    }
}
