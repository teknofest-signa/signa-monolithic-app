package teknofest.signa.producer.service;

import static teknofest.signa.producer.constants.ErrorConstants.ADMIN_NOT_FOUND;
import static teknofest.signa.producer.constants.ErrorConstants.EMAIL_ALREADY_EXISTS;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import teknofest.signa.producer.enums.NotificationType;
import teknofest.signa.producer.handler.exception.ApplicationException;
import teknofest.signa.producer.handler.exception.ResourceNotFoundException;
import teknofest.signa.producer.model.dto.auth.CreateAdminRequest;
import teknofest.signa.producer.model.dto.backoffice.AdminInfo;
import teknofest.signa.producer.model.dto.backoffice.AdminUpdateRequest;
import teknofest.signa.producer.model.entity.Admin;
import teknofest.signa.producer.enums.Role;
import teknofest.signa.producer.enums.Status;
import teknofest.signa.producer.model.event.NotificationEvent;
import teknofest.signa.producer.repository.AdminRepository;

@Slf4j
@Service
@RequiredArgsConstructor
public class SuperAdminService {

    public static final String USERNAME = "username";
    public static final String PASSWORD = "password";

    private final AdminRepository adminRepository;
    private final PasswordEncoder passwordEncoder;
    private final ApplicationEventPublisher applicationEventPublisher;

    public void createAdmin(CreateAdminRequest createAdminRequest) {
        if (adminRepository.existsByEmail(createAdminRequest.getEmail())) {
            throw new ApplicationException(EMAIL_ALREADY_EXISTS);
        }

        Admin admin = Admin.builder()
                .role(Role.ADMIN)
                .email(createAdminRequest.getEmail())
                .status(Status.PENDING)
                .username(USERNAME)
                .password(passwordEncoder.encode(PASSWORD))
                .token(UUID.randomUUID().toString())
                .build();
        adminRepository.save(admin);

        applicationEventPublisher.publishEvent(new NotificationEvent(
                admin.getEmail(),
                NotificationType.CREATE_ADMIN,
                Map.of(
                        "token", admin.getToken()
                )
        ));
    }

    public List<AdminInfo> getAllAdmins() {
        return adminRepository.findAll()
                .stream()
                .filter(admin -> admin.getRole().equals(Role.ADMIN))
                .map(this::toAdminInfo)
                .toList();
    }

    public AdminInfo getAdmin(UUID id) {
        Admin admin = adminRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(ADMIN_NOT_FOUND));

        return toAdminInfo(admin);
    }

    public void updateAdmin(AdminUpdateRequest adminUpdateRequest, UUID id) {
        Admin admin = adminRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(ADMIN_NOT_FOUND));

        if (adminUpdateRequest.getEmail() != null) {
            admin.setEmail(adminUpdateRequest.getEmail());
        }

        if (adminUpdateRequest.getUsername() != null) {
            admin.setUsername(adminUpdateRequest.getUsername());
        }

        if (adminUpdateRequest.getPassword() != null && !adminUpdateRequest.getPassword().isBlank()) {
            admin.setPassword(passwordEncoder.encode(adminUpdateRequest.getPassword()));
        }

        if (adminUpdateRequest.getStatus() != null) {
            admin.setStatus(adminUpdateRequest.getStatus());
        }

        adminRepository.save(admin);
    }

    public void deleteAdmin(UUID id) {
        if (!adminRepository.existsById(id)) {
            throw new ResourceNotFoundException(ADMIN_NOT_FOUND);
        }
        adminRepository.deleteById(id);
    }

    private AdminInfo toAdminInfo(Admin admin) {
        return AdminInfo.builder()
                .id(admin.getId())
                .email(admin.getEmail())
                .username(admin.getUsername())
                .status(admin.getStatus())
                .role(admin.getRole())
                .profilePhoto(admin.getProfilePhoto())
                .createdAt(admin.getCreatedAt())
                .updatedAt(admin.getUpdatedAt())
                .build();
    }
}
