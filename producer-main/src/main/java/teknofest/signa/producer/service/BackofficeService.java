package teknofest.signa.producer.service;

import static teknofest.signa.producer.constants.ErrorConstants.ADMIN_NOT_FOUND;
import static teknofest.signa.producer.constants.ErrorConstants.FAILED_TO_UPLOAD_PHOTO;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import teknofest.signa.producer.enums.Status;
import teknofest.signa.producer.handler.exception.ApplicationException;
import teknofest.signa.producer.handler.exception.ResourceNotFoundException;
import teknofest.signa.producer.model.dto.backoffice.InfoResponse;
import teknofest.signa.producer.model.dto.backoffice.RiskCheckDetailResponseDto;
import teknofest.signa.producer.model.dto.backoffice.RiskCheckSummaryResponseDto;
import teknofest.signa.producer.model.dto.backoffice.RiskFactorDetailDto;
import teknofest.signa.producer.model.dto.transaction.TransactionInfo;
import teknofest.signa.producer.model.dto.transaction.check.CheckTransactionAccountDto;
import teknofest.signa.producer.model.dto.transaction.check.CheckTransactionDetailsDto;
import teknofest.signa.producer.model.dto.transaction.check.CheckTransactionHistoryEntryDto;
import teknofest.signa.producer.model.dto.transaction.check.CheckTransactionRecipientDto;
import teknofest.signa.producer.model.dto.transaction.check.CheckTransactionUserDto;
import teknofest.signa.producer.model.entity.Admin;
import teknofest.signa.producer.model.entity.Transaction;
import teknofest.signa.producer.model.entity.TransactionRiskCheck;
import teknofest.signa.producer.model.entity.TransactionRiskCheckHistoryEntry;
import teknofest.signa.producer.repository.AdminRepository;
import teknofest.signa.producer.repository.TransactionRepository;
import teknofest.signa.producer.repository.TransactionRiskCheckRepository;

@Slf4j
@Service
@RequiredArgsConstructor
public class BackofficeService {

    private final static String SORT_FIELD = "createdAt";

    private final AdminRepository adminRepository;
    private final TransactionRepository transactionRepository;
    private final TransactionRiskCheckRepository transactionRiskCheckRepository;
    private final RiskCheckReportService riskCheckReportService;

    public InfoResponse getInfo(String email) {
        Admin admin = adminRepository.findByEmailAndStatus(email, Status.ACTIVE)
                .orElseThrow(() -> new ResourceNotFoundException(ADMIN_NOT_FOUND));

        return InfoResponse.builder()
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

    public void uploadProfilePhoto(String email, MultipartFile multipartFile) {
        Admin admin = adminRepository.findByEmailAndStatus(email, Status.ACTIVE)
                .orElseThrow(() -> new ResourceNotFoundException(ADMIN_NOT_FOUND));

        if (multipartFile == null || multipartFile.isEmpty()) {
            admin.setProfilePhoto(null);
            adminRepository.save(admin);
            return;
        }

        try {
            admin.setProfilePhoto(multipartFile.getBytes());
            adminRepository.save(admin);
        } catch (Exception exception) {
            throw new ApplicationException(FAILED_TO_UPLOAD_PHOTO);
        }
    }

    public Page<TransactionInfo> getAllTransactions(int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(SORT_FIELD).descending());
        return transactionRepository.findAll(pageable).map(this::toTransactionInfo);
    }

    @Transactional(readOnly = true)
    public Page<RiskCheckSummaryResponseDto> getRiskChecks(int page, int size, String status) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(SORT_FIELD).descending());
        Page<TransactionRiskCheck> resultPage;

        if ("APPROVED".equalsIgnoreCase(status)) {
            resultPage = transactionRiskCheckRepository.findByApproved(true, pageable);
        } else if ("FAILED".equalsIgnoreCase(status) || "BLOCKED".equalsIgnoreCase(status)) {
            resultPage = transactionRiskCheckRepository.findByApproved(false, pageable);
        } else {
            resultPage = transactionRiskCheckRepository.findAll(pageable);
        }

        return resultPage.map(this::toRiskCheckSummary);
    }

    @Transactional(readOnly = true)
    public RiskCheckDetailResponseDto getRiskCheckDetails(UUID id) {
        TransactionRiskCheck entity = transactionRiskCheckRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction risk check not found with ID: " + id));

        return buildRiskCheckDetails(entity);
    }

    @Transactional(readOnly = true)
    public byte[] exportRiskCheckExcel(UUID id) {
        RiskCheckDetailResponseDto details = getRiskCheckDetails(id);
        return riskCheckReportService.generateExcelReport(details);
    }

    @Transactional(readOnly = true)
    public byte[] exportRiskCheckReport(UUID id) {
        RiskCheckDetailResponseDto details = getRiskCheckDetails(id);
        return riskCheckReportService.generateHtmlAuditReport(details);
    }

    private RiskCheckSummaryResponseDto toRiskCheckSummary(TransactionRiskCheck entity) {
        return RiskCheckSummaryResponseDto.builder()
                .id(entity.getId())
                .userAccountId(entity.getUserAccountId())
                .userName(entity.getUserName())
                .userIban(entity.getUserIban())
                .transactionAmount(entity.getTransactionAmount())
                .transactionCurrency(entity.getTransactionCurrency())
                .recipientName(entity.getRecipientName())
                .recipientType(entity.getRecipientType())
                .recipientReference(entity.getRecipientReference())
                .approved(entity.isApproved())
                .reason(entity.getReason())
                .riskScore(entity.getRiskScore())
                .requestedAt(entity.getRequestedAt())
                .createdAt(entity.getCreatedAt())
                .build();
    }

    public RiskCheckDetailResponseDto buildRiskCheckDetails(TransactionRiskCheck entity) {
        CheckTransactionUserDto user = CheckTransactionUserDto.builder()
                .accountId(entity.getUserAccountId())
                .name(entity.getUserName())
                .iban(entity.getUserIban())
                .accountType(entity.getUserAccountType())
                .memberSince(entity.getUserMemberSince())
                .build();

        CheckTransactionAccountDto account = CheckTransactionAccountDto.builder()
                .currentBalance(entity.getAccountCurrentBalance())
                .currency(entity.getAccountCurrency())
                .build();

        CheckTransactionRecipientDto recipient = CheckTransactionRecipientDto.builder()
                .type(entity.getRecipientType())
                .name(entity.getRecipientName())
                .reference(entity.getRecipientReference())
                .build();

        CheckTransactionDetailsDto transaction = CheckTransactionDetailsDto.builder()
                .amount(entity.getTransactionAmount())
                .currency(entity.getTransactionCurrency())
                .note(entity.getTransactionNote())
                .recipient(recipient)
                .build();

        List<CheckTransactionHistoryEntryDto> historyEntries = new ArrayList<>();
        BigDecimal depositTotal = BigDecimal.ZERO;
        BigDecimal withdrawalTotal = BigDecimal.ZERO;
        List<BigDecimal> pastWithdrawals = new ArrayList<>();
        Map<String, BigDecimal> categoryBreakdown = new HashMap<>();
        Map<String, Integer> categoryCountBreakdown = new HashMap<>();

        if (entity.getHistoryEntries() != null) {
            for (TransactionRiskCheckHistoryEntry h : entity.getHistoryEntries()) {
                if ("TRANSACTION".equalsIgnoreCase(h.getHistoryType())) {
                    historyEntries.add(CheckTransactionHistoryEntryDto.builder()
                            .name(h.getName())
                            .category(h.getCategory())
                            .date(h.getTransactionDate())
                            .amt(h.getAmount())
                            .initials(h.getInitials())
                            .build());
                }

                if (h.getAmount() != null) {
                    if (h.getAmount().compareTo(BigDecimal.ZERO) > 0) {
                        if ("DEPOSIT".equalsIgnoreCase(h.getHistoryType())) {
                            depositTotal = depositTotal.add(h.getAmount());
                        }
                    } else if (h.getAmount().compareTo(BigDecimal.ZERO) < 0) {
                        if ("WITHDRAWAL".equalsIgnoreCase(h.getHistoryType())) {
                            BigDecimal absAmt = h.getAmount().abs();
                            withdrawalTotal = withdrawalTotal.add(absAmt);
                            pastWithdrawals.add(absAmt);
                        }
                    }

                    if (h.getCategory() != null && !h.getCategory().isBlank()) {
                        categoryBreakdown.put(h.getCategory(),
                                categoryBreakdown.getOrDefault(h.getCategory(), BigDecimal.ZERO).add(h.getAmount().abs()));
                        categoryCountBreakdown.put(h.getCategory(),
                                categoryCountBreakdown.getOrDefault(h.getCategory(), 0) + 1);
                    }
                }
            }
        }

        // If history had no explicit DEPOSIT/WITHDRAWAL types, calculate from list
        if (depositTotal.compareTo(BigDecimal.ZERO) == 0 && withdrawalTotal.compareTo(BigDecimal.ZERO) == 0) {
            for (CheckTransactionHistoryEntryDto h : historyEntries) {
                if (h.getAmt() != null) {
                    if (h.getAmt().compareTo(BigDecimal.ZERO) > 0) {
                        depositTotal = depositTotal.add(h.getAmt());
                    } else {
                        BigDecimal absAmt = h.getAmt().abs();
                        withdrawalTotal = withdrawalTotal.add(absAmt);
                        pastWithdrawals.add(absAmt);
                    }
                }
            }
        }

        BigDecimal avgWithdrawal = BigDecimal.ZERO;
        BigDecimal maxWithdrawal = BigDecimal.ZERO;
        if (!pastWithdrawals.isEmpty()) {
            BigDecimal sum = pastWithdrawals.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
            avgWithdrawal = sum.divide(BigDecimal.valueOf(pastWithdrawals.size()), 2, RoundingMode.HALF_UP);
            maxWithdrawal = pastWithdrawals.stream().reduce(BigDecimal.ZERO, BigDecimal::max);
        }

        BigDecimal amount = entity.getTransactionAmount() != null ? entity.getTransactionAmount() : BigDecimal.ZERO;
        BigDecimal balance = entity.getAccountCurrentBalance() != null ? entity.getAccountCurrentBalance() : BigDecimal.ZERO;
        BigDecimal projectedBalance = balance.subtract(amount);

        // Build Explanation of Risk Factors
        List<RiskFactorDetailDto> riskFactors = buildRiskFactorExplanations(entity, historyEntries, avgWithdrawal, maxWithdrawal, amount, balance);

        return RiskCheckDetailResponseDto.builder()
                .id(entity.getId())
                .user(user)
                .account(account)
                .transaction(transaction)
                .approved(entity.isApproved())
                .reason(entity.getReason())
                .riskScore(entity.getRiskScore() != null ? entity.getRiskScore() : BigDecimal.ZERO)
                .riskFactors(riskFactors)
                .requestedAt(entity.getRequestedAt())
                .createdAt(entity.getCreatedAt())
                .historyEntries(historyEntries)
                .depositTotal(depositTotal)
                .withdrawalTotal(withdrawalTotal)
                .avgWithdrawal(avgWithdrawal)
                .maxWithdrawal(maxWithdrawal)
                .projectedBalance(projectedBalance)
                .categoryBreakdown(categoryBreakdown)
                .categoryCountBreakdown(categoryCountBreakdown)
                .build();
    }

    private List<RiskFactorDetailDto> buildRiskFactorExplanations(
            TransactionRiskCheck entity,
            List<CheckTransactionHistoryEntryDto> historyEntries,
            BigDecimal avgWithdrawal,
            BigDecimal maxWithdrawal,
            BigDecimal amount,
            BigDecimal balance) {

        List<RiskFactorDetailDto> factors = new ArrayList<>();

        boolean insufficientFunds = balance.compareTo(BigDecimal.ZERO) >= 0 && amount.compareTo(balance) > 0;
        factors.add(RiskFactorDetailDto.builder()
                .code("INSUFFICIENT_FUNDS")
                .title("Account Balance Coverage")
                .description(insufficientFunds
                        ? "Transaction amount (" + amount + ") exceeds current available balance (" + balance + ")."
                        : "Account has sufficient funds to cover the transfer.")
                .severity("CRITICAL")
                .riskWeight(BigDecimal.ONE)
                .triggered(insufficientFunds)
                .build());

        boolean isNewRecipient = "new_iban".equalsIgnoreCase(entity.getRecipientType())
                || (entity.getRecipientName() != null && historyEntries.stream()
                .noneMatch(h -> h.getName() != null && h.getName().equalsIgnoreCase(entity.getRecipientName())));

        factors.add(RiskFactorDetailDto.builder()
                .code("NEW_RECIPIENT")
                .title("New / Unverified Payee")
                .description(isNewRecipient
                        ? "The recipient is a fresh IBAN or has never received transactions from this account."
                        : "Recipient is a known counterparty found in historical transactions.")
                .severity("MEDIUM")
                .riskWeight(BigDecimal.valueOf(0.25))
                .triggered(isNewRecipient)
                .build());

        boolean avgSpendAnomaly = isNewRecipient && avgWithdrawal.compareTo(BigDecimal.ZERO) > 0
                && amount.compareTo(avgWithdrawal.multiply(BigDecimal.valueOf(5))) > 0;
        factors.add(RiskFactorDetailDto.builder()
                .code("SPEND_ANOMALY_AVG")
                .title("Unusual Amount vs Average Spend")
                .description(avgSpendAnomaly
                        ? "Transaction amount is over 5x higher than user's average withdrawal (" + avgWithdrawal + ")."
                        : "Amount aligns within standard deviation of historical average spending.")
                .severity("HIGH")
                .riskWeight(BigDecimal.valueOf(0.35))
                .triggered(avgSpendAnomaly)
                .build());

        boolean maxSpendAnomaly = isNewRecipient && maxWithdrawal.compareTo(BigDecimal.ZERO) > 0
                && amount.compareTo(maxWithdrawal.multiply(BigDecimal.valueOf(2.5))) > 0;
        factors.add(RiskFactorDetailDto.builder()
                .code("SPEND_ANOMALY_MAX")
                .title("Breach of Maximum Historical Spend")
                .description(maxSpendAnomaly
                        ? "Transaction amount is over 2.5x larger than highest previous withdrawal (" + maxWithdrawal + ")."
                        : "Amount does not exceed abnormal multiples of peak historical spending.")
                .severity("HIGH")
                .riskWeight(BigDecimal.valueOf(0.30))
                .triggered(maxSpendAnomaly)
                .build());

        boolean balanceDrain = balance.compareTo(BigDecimal.ZERO) > 0
                && amount.compareTo(balance.multiply(BigDecimal.valueOf(0.70))) > 0;
        factors.add(RiskFactorDetailDto.builder()
                .code("BALANCE_DRAIN")
                .title("High Balance Depletion")
                .description(balanceDrain
                        ? "Transfer consumes more than 70% of total available account balance."
                        : "Transfer leaves a healthy balance reserve in the account.")
                .severity("MEDIUM")
                .riskWeight(BigDecimal.valueOf(0.25))
                .triggered(balanceDrain)
                .build());

        boolean highValue = amount.compareTo(BigDecimal.valueOf(10_000)) > 0;
        factors.add(RiskFactorDetailDto.builder()
                .code("HIGH_AMOUNT_THRESHOLD")
                .title("High-Value Transfer Threshold")
                .description(highValue
                        ? "Transfer amount exceeds $10,000 threshold for automated baseline risk."
                        : "Transfer is within normal transaction size bounds.")
                .severity("MEDIUM")
                .riskWeight(BigDecimal.valueOf(0.30))
                .triggered(highValue)
                .build());

        boolean extremeValue = amount.compareTo(BigDecimal.valueOf(50_000)) > 0;
        factors.add(RiskFactorDetailDto.builder()
                .code("EXTREME_AMOUNT_THRESHOLD")
                .title("Extreme-Value AML Threshold")
                .description(extremeValue
                        ? "Transfer amount exceeds $50,000 extreme single transaction limit."
                        : "Transfer is below critical institutional AML limit.")
                .severity("CRITICAL")
                .riskWeight(BigDecimal.valueOf(0.40))
                .triggered(extremeValue)
                .build());

        return factors;
    }

    private TransactionInfo toTransactionInfo(Transaction transaction) {
        return TransactionInfo.builder()
                .id(transaction.getId())
                .fraudScore(transaction.getFraudScore())
                .transactionStatus(transaction.getTransactionStatus())
                .transactionType(transaction.getTransactionType())
                .amount(transaction.getAmount())
                .currency(transaction.getCurrency())
                .toAccountId(transaction.getToAccountId())
                .fromAccountId(transaction.getFromAccountId())
                .referenceId(transaction.getReferenceId())
                .description(transaction.getDescription())
                .createdAt(transaction.getCreatedAt())
                .updatedAt(transaction.getUpdatedAt())
                .build();
    }
}
