package teknofest.signa.producer.controller;

import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import teknofest.signa.producer.model.dto.customer.CustomerInfo;
import teknofest.signa.producer.model.dto.customer.RegisterCustomerRequest;
import teknofest.signa.producer.service.CustomerService;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/customers")
public class CustomerController {

    private final CustomerService customerService;

    @GetMapping
    public Page<CustomerInfo> getAllCustomers(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "10") int size) {
        return customerService.getAllCustomers(page, size);
    }

    @PostMapping
    public void registerCustomer(@Valid @RequestBody RegisterCustomerRequest registerCustomerRequest) {
        customerService.registerCustomer(registerCustomerRequest);
    }

    @PutMapping("/{id}")
    public void blockCustomer(@PathVariable UUID id) {
        customerService.blockCustomer(id);
    }
}
