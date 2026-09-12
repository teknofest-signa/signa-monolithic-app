package teknofest.signa.producer.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import teknofest.signa.producer.model.dto.transaction.check.CheckTransactionRequestDto;
import teknofest.signa.producer.model.dto.transaction.check.CheckTransactionResponseDto;
import teknofest.signa.producer.service.TransactionService;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/transactions")
public class TransactionController {

    private final TransactionService transactionService;

    @PostMapping("/risk-check")
    public CheckTransactionResponseDto checkTransaction(@RequestBody CheckTransactionRequestDto checkTransactionRequestDto) {
        return transactionService.checkTransaction(checkTransactionRequestDto);
    }
}
