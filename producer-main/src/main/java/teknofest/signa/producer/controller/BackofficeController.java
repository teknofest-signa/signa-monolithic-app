package teknofest.signa.producer.controller;

import java.security.Principal;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import teknofest.signa.producer.model.dto.backoffice.InfoResponse;
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
}
