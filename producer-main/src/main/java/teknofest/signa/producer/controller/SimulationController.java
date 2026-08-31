package teknofest.signa.producer.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import teknofest.signa.producer.model.dto.simulation.SimulateTransactionRequest;
import teknofest.signa.producer.model.dto.simulation.SimulateTransactionResponse;
import teknofest.signa.producer.model.dto.simulation.SimulationTransactionInfo;
import teknofest.signa.producer.service.SimulationService;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/simulation")
public class SimulationController {

    private final SimulationService simulationService;

    @PostMapping("/transactions")
    public SimulateTransactionResponse simulateTransaction(@Valid @RequestBody SimulateTransactionRequest simulateTransactionRequest) {
        return simulationService.simulateTransaction(simulateTransactionRequest);
    }

    @GetMapping("/transactions")
    public Page<SimulationTransactionInfo> getAllSimulationTransactions(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "10") int size) {
        return simulationService.getAllSimulationTransactions(page, size);
    }
}
