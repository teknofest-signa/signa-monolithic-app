package teknofest.signa.producer.controller;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import teknofest.signa.producer.model.dto.auth.CreateAdminRequest;
import teknofest.signa.producer.model.dto.backoffice.AdminInfo;
import teknofest.signa.producer.model.dto.backoffice.AdminUpdateRequest;
import teknofest.signa.producer.service.SuperAdminService;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/super-admins")
public class SuperAdminController {

    private final SuperAdminService superAdminService;

    @PostMapping("/admins")
    public void createAdmin(@Valid @RequestBody CreateAdminRequest createAdminRequest) {
        superAdminService.createAdmin(createAdminRequest);
    }

    @GetMapping("/admins")
    public List<AdminInfo> getAllAdmins() {
        return superAdminService.getAllAdmins();
    }

    @GetMapping("/admins/{id}")
    public AdminInfo getAdmin(@PathVariable UUID id) {
        return superAdminService.getAdmin(id);
    }

    @PutMapping("/admins/{id}")
    public void updateAdmin(@Valid @RequestBody AdminUpdateRequest adminUpdateRequest, @PathVariable UUID id) {
        superAdminService.updateAdmin(adminUpdateRequest, id);
    }

    @DeleteMapping("/admins/{id}")
    public void deleteAdmin(@PathVariable UUID id) {
        superAdminService.deleteAdmin(id);
    }
}
