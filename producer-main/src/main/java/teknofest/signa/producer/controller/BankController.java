package teknofest.signa.producer.controller;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import teknofest.signa.producer.model.dto.bank.BankCredentialsResponse;
import teknofest.signa.producer.model.dto.bank.BankInfo;
import teknofest.signa.producer.model.dto.bank.CreateBankRequest;
import teknofest.signa.producer.service.BankService;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/banks")
public class BankController {

    private final BankService bankService;

    @PostMapping
    public BankCredentialsResponse createBank(@Valid @RequestBody CreateBankRequest createBankRequest) {
        return bankService.createBank(createBankRequest);
    }

    /**
     * Issues a fresh OPRF API key, invalidating the previous one. Restricted to
     * SUPER_ADMIN: rotating a key cuts a live bank off from the network until
     * it deploys the new one.
     */
    @PostMapping("/{id}/rotate-api-key")
    @PreAuthorize("hasAuthority('SUPER_ADMIN')")
    public BankCredentialsResponse rotateApiKey(@PathVariable UUID id) {
        return bankService.rotateApiKey(id);
    }

    /** Suspends or restores a bank's OPRF access. */
    @PutMapping("/{id}/oprf-access")
    @PreAuthorize("hasAuthority('SUPER_ADMIN')")
    public void setOprfAccess(@PathVariable UUID id, @RequestParam boolean enabled) {
        bankService.setOprfEnabled(id, enabled);
    }

    @GetMapping
    public List<BankInfo> getAllBanks() {
        return bankService.getAllBanks();
    }

    @DeleteMapping("/{id}")
    public void deleteBank(@PathVariable UUID id) {
        bankService.deleteBank(id);
    }

    @PostMapping(value = "/{id}/upload-photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public void uploadLogo(@PathVariable UUID id, @RequestParam(value = "file", required = false) MultipartFile multipartFile) {
        bankService.uploadLogo(id, multipartFile);
    }
}
