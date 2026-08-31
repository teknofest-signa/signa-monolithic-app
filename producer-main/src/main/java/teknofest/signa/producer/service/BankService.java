package teknofest.signa.producer.service;

import static teknofest.signa.producer.constants.ErrorConstants.BANK_NOT_FOUND;
import static teknofest.signa.producer.constants.ErrorConstants.FAILED_TO_UPLOAD_PHOTO;

import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import teknofest.signa.producer.handler.exception.ApplicationException;
import teknofest.signa.producer.handler.exception.ResourceNotFoundException;
import teknofest.signa.producer.model.dto.bank.BankCredentialsResponse;
import teknofest.signa.producer.model.dto.bank.BankInfo;
import teknofest.signa.producer.model.dto.bank.CreateBankRequest;
import teknofest.signa.producer.model.entity.Bank;
import teknofest.signa.producer.repository.BankRepository;

@Slf4j
@Service
@RequiredArgsConstructor
public class BankService {

    private final BankRepository bankRepository;
    private final BankApiKeyService bankApiKeyService;

    /**
     * Registers a member bank and issues its OPRF credentials in the same step.
     *
     * <p>Provisioning the credential with the bank rather than as a later
     * opt-in means there is no window in which a bank exists without an
     * identity the OPRF endpoint can rate limit and attribute.
     *
     * @return the credentials, including the only readable copy of the API key
     */
    @Transactional
    public BankCredentialsResponse createBank(CreateBankRequest createBankRequest) {
        Bank bank = Bank.builder()
                .name(createBankRequest.getName())
                .clientId(bankApiKeyService.generateClientId())
                .oprfEnabled(true)
                .build();
        bankRepository.save(bank);

        String apiKey = bankApiKeyService.issueApiKey(bank);
        log.info("Member bank {} registered with clientId {}", bank.getId(), bank.getClientId());

        return BankCredentialsResponse.builder()
                .bankId(bank.getId())
                .name(bank.getName())
                .clientId(bank.getClientId())
                .apiKey(apiKey)
                .build();
    }

    /**
     * Replaces a bank's API key, invalidating the previous one immediately.
     * The path out of a suspected credential compromise.
     */
    @Transactional
    public BankCredentialsResponse rotateApiKey(UUID bankId) {
        Bank bank = bankRepository.findById(bankId)
                .orElseThrow(() -> new ResourceNotFoundException(BANK_NOT_FOUND));

        String apiKey = bankApiKeyService.rotateApiKey(bankId);

        return BankCredentialsResponse.builder()
                .bankId(bank.getId())
                .name(bank.getName())
                .clientId(bank.getClientId())
                .apiKey(apiKey)
                .build();
    }

    /**
     * Suspends or restores a bank's OPRF access without deleting the
     * institution, so an enumeration attempt can be cut off while its records
     * and audit trail stay intact for investigation.
     */
    @Transactional
    public void setOprfEnabled(UUID bankId, boolean enabled) {
        Bank bank = bankRepository.findById(bankId)
                .orElseThrow(() -> new ResourceNotFoundException(BANK_NOT_FOUND));

        bank.setOprfEnabled(enabled);
        bankRepository.save(bank);
        log.warn("OPRF access for bank {} set to {}", bankId, enabled);
    }

    public List<BankInfo> getAllBanks() {
        return bankRepository.findAll()
                .stream()
                .map(this::toBankInfo)
                .toList();
    }

    public void deleteBank(UUID bankId) {
        if (!bankRepository.existsById(bankId)) {
            throw new ResourceNotFoundException(BANK_NOT_FOUND);
        }
        bankRepository.deleteById(bankId);
    }

    public void uploadLogo(UUID id, MultipartFile multipartFile) {
        Bank bank = bankRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(BANK_NOT_FOUND));

        if (multipartFile == null || multipartFile.isEmpty()) {
            bank.setLogo(null);
            bankRepository.save(bank);
            return;
        }

        try {
            bank.setLogo(multipartFile.getBytes());
            bankRepository.save(bank);
        } catch (Exception exception) {
            throw new ApplicationException(FAILED_TO_UPLOAD_PHOTO);
        }
    }

    private BankInfo toBankInfo(Bank bank) {
        return BankInfo.builder()
                .id(bank.getId())
                .name(bank.getName())
                .clientId(bank.getClientId())
                .oprfEnabled(bank.isOprfEnabled())
                .logo(bank.getLogo())
                .createdAt(bank.getCreatedAt())
                .updatedAt(bank.getUpdatedAt())
                .build();
    }
}
