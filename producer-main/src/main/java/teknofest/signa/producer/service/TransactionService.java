package teknofest.signa.producer.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import teknofest.signa.producer.model.dto.transaction.check.CheckTransactionAccountDto;
import teknofest.signa.producer.model.dto.transaction.check.CheckTransactionDetailsDto;
import teknofest.signa.producer.model.dto.transaction.check.CheckTransactionHistoryEntryDto;
import teknofest.signa.producer.model.dto.transaction.check.CheckTransactionRecipientDto;
import teknofest.signa.producer.model.dto.transaction.check.CheckTransactionRequestDto;
import teknofest.signa.producer.model.dto.transaction.check.CheckTransactionResponseDto;
import teknofest.signa.producer.model.dto.transaction.check.CheckTransactionUserDto;
import teknofest.signa.producer.model.entity.TransactionRiskCheck;
import teknofest.signa.producer.model.entity.TransactionRiskCheckHistoryEntry;
import teknofest.signa.producer.repository.TransactionRiskCheckRepository;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionService {

    private static final String RECIPIENT_TYPE_NEW_IBAN = "new_iban";
    private static final String REASON_NEW_RECIPIENT_UNUSUAL_AMOUNT =
            "This recipient hasn't been used before and the amount is unusually large for this account.";
    private static final String REASON_INSUFFICIENT_FUNDS =
            "Transaction amount exceeds available account balance.";
    private static final String REASON_INVALID_AMOUNT =
            "Transaction amount must be greater than zero.";
    private static final String REASON_HIGH_AMOUNT_THRESHOLD =
            "Transaction amount exceeds the standard threshold for automated approval.";
    private static final String REASON_RULE_FLAGGED =
            "Transaction flagged by risk management rules.";

    private final TransactionRiskCheckRepository transactionRiskCheckRepository;

    @Transactional
    public CheckTransactionResponseDto checkTransaction(CheckTransactionRequestDto request) {
        if (request == null || request.getTransaction() == null) {
            return CheckTransactionResponseDto.builder()
                    .approved(false)
                    .reason(REASON_INVALID_AMOUNT)
                    .build();
        }

        CheckTransactionDetailsDto tx = request.getTransaction();
        CheckTransactionAccountDto account = request.getAccount();
        CheckTransactionRecipientDto recipient = tx.getRecipient();

        BigDecimal amount = tx.getAmount();
        BigDecimal balance = (account != null && account.getCurrentBalance() != null)
                ? account.getCurrentBalance()
                : BigDecimal.ZERO;

        boolean approved = true;
        String reason = null;
        BigDecimal riskScore = BigDecimal.ZERO;

        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            approved = false;
            reason = REASON_INVALID_AMOUNT;
            riskScore = BigDecimal.ONE;
        } else if (account != null && account.getCurrentBalance() != null && amount.compareTo(balance) > 0) {
            approved = false;
            reason = REASON_INSUFFICIENT_FUNDS;
            riskScore = BigDecimal.ONE;
        } else {
            // Evaluate risk heuristics
            boolean isNewRecipient = isNewRecipient(recipient, request.getTransactionHistory(), request.getWithdrawalHistory());

            // Spending analysis from withdrawal history
            List<BigDecimal> pastWithdrawals = extractPastWithdrawalAmounts(request.getWithdrawalHistory(), request.getTransactionHistory());

            BigDecimal avgWithdrawal = BigDecimal.ZERO;
            BigDecimal maxWithdrawal = BigDecimal.ZERO;

            if (!pastWithdrawals.isEmpty()) {
                BigDecimal sum = pastWithdrawals.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
                avgWithdrawal = sum.divide(BigDecimal.valueOf(pastWithdrawals.size()), 4, RoundingMode.HALF_UP);
                maxWithdrawal = pastWithdrawals.stream().reduce(BigDecimal.ZERO, BigDecimal::max);
            }

            if (isNewRecipient) {
                riskScore = riskScore.add(BigDecimal.valueOf(0.25));

                if (!pastWithdrawals.isEmpty()) {
                    if (avgWithdrawal.compareTo(BigDecimal.ZERO) > 0 && amount.compareTo(avgWithdrawal.multiply(BigDecimal.valueOf(5))) > 0) {
                        riskScore = riskScore.add(BigDecimal.valueOf(0.35));
                    } else if (maxWithdrawal.compareTo(BigDecimal.ZERO) > 0 && amount.compareTo(maxWithdrawal.multiply(BigDecimal.valueOf(2.5))) > 0) {
                        riskScore = riskScore.add(BigDecimal.valueOf(0.30));
                    }

                    if (balance.compareTo(BigDecimal.ZERO) > 0 && amount.compareTo(balance.multiply(BigDecimal.valueOf(0.70))) > 0) {
                        riskScore = riskScore.add(BigDecimal.valueOf(0.25));
                    }
                } else {
                    if (balance.compareTo(BigDecimal.ZERO) > 0 && amount.compareTo(balance.multiply(BigDecimal.valueOf(0.50))) > 0) {
                        riskScore = riskScore.add(BigDecimal.valueOf(0.35));
                    }
                }

                if (recipient != null && RECIPIENT_TYPE_NEW_IBAN.equalsIgnoreCase(recipient.getType()) && amount.compareTo(BigDecimal.valueOf(5_000)) > 0) {
                    riskScore = riskScore.add(BigDecimal.valueOf(0.30));
                }
            }

            if (amount.compareTo(BigDecimal.valueOf(10_000)) > 0) {
                riskScore = riskScore.add(BigDecimal.valueOf(0.30));
            }

            if (amount.compareTo(BigDecimal.valueOf(50_000)) > 0) {
                riskScore = riskScore.add(BigDecimal.valueOf(0.40));
            }

            if (riskScore.compareTo(BigDecimal.valueOf(0.50)) >= 0) {
                approved = false;
                if (isNewRecipient && (riskScore.compareTo(BigDecimal.valueOf(0.55)) >= 0 || amount.compareTo(BigDecimal.valueOf(5_000)) > 0)) {
                    reason = REASON_NEW_RECIPIENT_UNUSUAL_AMOUNT;
                } else if (amount.compareTo(BigDecimal.valueOf(50_000)) > 0) {
                    reason = REASON_HIGH_AMOUNT_THRESHOLD;
                } else {
                    reason = REASON_RULE_FLAGGED;
                }
            }
        }

        TransactionRiskCheck entity = buildEntity(request, approved, reason, riskScore);
        transactionRiskCheckRepository.save(entity);

        log.info("Transaction risk check completed. Approved: {}, Reason: {}, Risk Score: {}", approved, reason, riskScore);

        return CheckTransactionResponseDto.builder()
                .approved(approved)
                .reason(reason)
                .build();
    }

    private boolean isNewRecipient(CheckTransactionRecipientDto recipient,
                                   List<CheckTransactionHistoryEntryDto> txHistory,
                                   List<CheckTransactionHistoryEntryDto> withdrawalHistory) {
        if (recipient == null) {
            return false;
        }

        if (RECIPIENT_TYPE_NEW_IBAN.equalsIgnoreCase(recipient.getType())) {
            return true;
        }

        String recipientName = recipient.getName();
        if (recipientName == null || recipientName.isBlank()) {
            return false;
        }

        boolean foundInWithdrawals = withdrawalHistory != null && withdrawalHistory.stream()
                .anyMatch(h -> h.getName() != null && h.getName().trim().equalsIgnoreCase(recipientName.trim()));

        boolean foundInTxHistory = txHistory != null && txHistory.stream()
                .anyMatch(h -> h.getName() != null && h.getName().trim().equalsIgnoreCase(recipientName.trim()));

        return !foundInWithdrawals && !foundInTxHistory;
    }

    private List<BigDecimal> extractPastWithdrawalAmounts(List<CheckTransactionHistoryEntryDto> withdrawalHistory,
                                                          List<CheckTransactionHistoryEntryDto> txHistory) {
        List<BigDecimal> amounts = new ArrayList<>();

        if (withdrawalHistory != null && !withdrawalHistory.isEmpty()) {
            for (CheckTransactionHistoryEntryDto entry : withdrawalHistory) {
                if (entry.getAmt() != null) {
                    amounts.add(entry.getAmt().abs());
                }
            }
        } else if (txHistory != null) {
            for (CheckTransactionHistoryEntryDto entry : txHistory) {
                if (entry.getAmt() != null && entry.getAmt().compareTo(BigDecimal.ZERO) < 0) {
                    amounts.add(entry.getAmt().abs());
                }
            }
        }

        return amounts;
    }

    private TransactionRiskCheck buildEntity(CheckTransactionRequestDto request,
                                            boolean approved,
                                            String reason,
                                            BigDecimal riskScore) {
        CheckTransactionUserDto user = request.getUser();
        CheckTransactionAccountDto account = request.getAccount();
        CheckTransactionDetailsDto tx = request.getTransaction();
        CheckTransactionRecipientDto recipient = tx != null ? tx.getRecipient() : null;

        TransactionRiskCheck entity = TransactionRiskCheck.builder()
                .userAccountId(user != null ? user.getAccountId() : null)
                .userName(user != null ? user.getName() : null)
                .userIban(user != null ? user.getIban() : null)
                .userAccountType(user != null ? user.getAccountType() : null)
                .userMemberSince(user != null ? user.getMemberSince() : null)
                .accountCurrentBalance(account != null ? account.getCurrentBalance() : null)
                .accountCurrency(account != null ? account.getCurrency() : null)
                .transactionAmount(tx != null ? tx.getAmount() : null)
                .transactionCurrency(tx != null ? tx.getCurrency() : null)
                .transactionNote(tx != null ? tx.getNote() : null)
                .recipientType(recipient != null ? recipient.getType() : null)
                .recipientName(recipient != null ? recipient.getName() : null)
                .recipientReference(recipient != null ? recipient.getReference() : null)
                .requestedAt(request.getRequestedAt())
                .approved(approved)
                .reason(reason)
                .riskScore(riskScore)
                .historyEntries(new ArrayList<>())
                .build();

        if (request.getTransactionHistory() != null) {
            for (CheckTransactionHistoryEntryDto entry : request.getTransactionHistory()) {
                entity.getHistoryEntries().add(toHistoryEntity(entity, entry, "TRANSACTION"));
            }
        }

        if (request.getDepositHistory() != null) {
            for (CheckTransactionHistoryEntryDto entry : request.getDepositHistory()) {
                entity.getHistoryEntries().add(toHistoryEntity(entity, entry, "DEPOSIT"));
            }
        }

        if (request.getWithdrawalHistory() != null) {
            for (CheckTransactionHistoryEntryDto entry : request.getWithdrawalHistory()) {
                entity.getHistoryEntries().add(toHistoryEntity(entity, entry, "WITHDRAWAL"));
            }
        }

        return entity;
    }

    private TransactionRiskCheckHistoryEntry toHistoryEntity(TransactionRiskCheck parent,
                                                            CheckTransactionHistoryEntryDto entry,
                                                            String historyType) {
        return TransactionRiskCheckHistoryEntry.builder()
                .riskCheck(parent)
                .historyType(historyType)
                .name(entry.getName())
                .category(entry.getCategory())
                .transactionDate(entry.getDate())
                .amount(entry.getAmt())
                .initials(entry.getInitials())
                .build();
    }
}
