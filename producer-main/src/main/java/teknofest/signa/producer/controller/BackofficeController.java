package teknofest.signa.producer.controller;

import java.security.Principal;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import teknofest.signa.producer.model.dto.backoffice.InfoResponse;
import teknofest.signa.producer.model.dto.backoffice.RiskCheckDetailResponseDto;
import teknofest.signa.producer.model.dto.backoffice.RiskCheckSummaryResponseDto;
import teknofest.signa.producer.model.dto.transaction.TransactionInfo;
import teknofest.signa.producer.service.BackofficeService;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/backoffice")
public class BackofficeController {

    private final BackofficeService backofficeService;

    @GetMapping
    public InfoResponse getInfo(Principal principal) {
        return backofficeService.getInfo(principal.getName());
    }

    @PostMapping(value = "/upload-photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public void uploadPhotoProfile(Principal principal, @RequestParam(value = "file", required = false) MultipartFile multipartFile) {
        backofficeService.uploadProfilePhoto(principal.getName(), multipartFile);
    }

    @GetMapping("/transactions")
    public Page<TransactionInfo> getAllTransactions(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "10") int size) {
        return backofficeService.getAllTransactions(page, size);
    }

    @GetMapping("/risk-checks")
    public Page<RiskCheckSummaryResponseDto> getRiskChecks(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String status) {
        return backofficeService.getRiskChecks(page, size, status);
    }

    @GetMapping("/risk-checks/{id}")
    public RiskCheckDetailResponseDto getRiskCheckDetails(@PathVariable UUID id) {
        return backofficeService.getRiskCheckDetails(id);
    }

    @GetMapping("/risk-checks/{id}/export/excel")
    public ResponseEntity<byte[]> exportRiskCheckExcel(@PathVariable UUID id) {
        byte[] bytes = backofficeService.exportRiskCheckExcel(id);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"risk-check-" + id + ".xls\"")
                .contentType(MediaType.parseMediaType("application/vnd.ms-excel"))
                .body(bytes);
    }

    @GetMapping("/risk-checks/{id}/export/report")
    public ResponseEntity<byte[]> exportRiskCheckReport(@PathVariable UUID id) {
        byte[] bytes = backofficeService.exportRiskCheckReport(id);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"risk-audit-" + id + ".html\"")
                .contentType(MediaType.TEXT_HTML)
                .body(bytes);
    }
}
