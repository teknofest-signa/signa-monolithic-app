package teknofest.signa.producer.service;

import static teknofest.signa.producer.constants.ErrorConstants.BANK_NOT_FOUND;
import static teknofest.signa.producer.constants.ErrorConstants.CUSTOMER_ALREADY_ENROLLED;
import static teknofest.signa.producer.constants.ErrorConstants.CUSTOMER_NOT_FOUND;
import static teknofest.signa.producer.constants.ErrorConstants.UNKNOWN_OPRF_KEY;

import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import teknofest.signa.producer.enums.CustomerStatus;
import teknofest.signa.producer.handler.exception.ApplicationException;
import teknofest.signa.producer.handler.exception.ResourceNotFoundException;
import teknofest.signa.producer.model.dto.customer.CustomerInfo;
import teknofest.signa.producer.model.dto.customer.RegisterCustomerRequest;
import teknofest.signa.producer.model.entity.Bank;
import teknofest.signa.producer.model.entity.Customer;
import teknofest.signa.producer.repository.BankRepository;
import teknofest.signa.producer.repository.CustomerRepository;

@Slf4j
@Service
@RequiredArgsConstructor
public class CustomerService {

    private final static String SORT_FIELD = "createdAt";

    private final BankRepository bankRepository;
    private final CustomerRepository customerRepository;
    private final OprfService oprfService;

    public Page<CustomerInfo> getAllCustomers(int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(SORT_FIELD).descending());
        return customerRepository.findAll(pageable).map(this::toCustomerInfo);
    }

    /**
     * Pseudonyms are omitted from read responses on purpose. A pseudonym is a
     * stable identifier for a person across every member bank, so handing the
     * full set to any authenticated operator would rebuild the cross-bank
     * linkage table in the client. Matching happens server-side, and the API
     * returns the answer, not the identifiers.
     */
    private CustomerInfo toCustomerInfo(Customer customer) {
        return CustomerInfo.builder()
                .id(customer.getId())
                .bankId(customer.getBankId())
                .bankName(customer.getBankName())
                .name(customer.getName())
                .oprfKeyId(customer.getOprfKeyId())
                .customerStatus(customer.getCustomerStatus())
                .createdAt(customer.getCreatedAt())
                .build();
    }

    @Transactional
    public void registerCustomer(RegisterCustomerRequest registerCustomerRequest) {
        Bank bank = bankRepository.findById(registerCustomerRequest.getBankId())
                .orElseThrow(() -> new ResourceNotFoundException(BANK_NOT_FOUND));

        // A pseudonym derived under a key this server does not hold can never
        // be matched against anything, so it is rejected at the door rather
        // than stored as a row that silently matches nobody.
        if (!oprfService.isKnownKeyId(registerCustomerRequest.getOprfKeyId())) {
            throw new ApplicationException(UNKNOWN_OPRF_KEY);
        }

        // One row per person per bank. Duplicates would make a block at one
        // bank suspend only some of that person's records, and would inflate
        // the linkage counts an analyst reads.
        boolean alreadyEnrolled = customerRepository.existsByBankIdAndPseudonymAndOprfKeyId(
                bank.getId(), registerCustomerRequest.getPseudonym(), registerCustomerRequest.getOprfKeyId());
        if (alreadyEnrolled) {
            throw new ApplicationException(CUSTOMER_ALREADY_ENROLLED);
        }

        Customer customer = Customer.builder()
                .name(registerCustomerRequest.getName())
                .bankId(bank.getId())
                .bankName(bank.getName())
                .pseudonym(registerCustomerRequest.getPseudonym())
                .oprfKeyId(registerCustomerRequest.getOprfKeyId())
                .customerStatus(CustomerStatus.ACTIVE)
                .build();
        customerRepository.save(customer);

        log.info("Customer enrolled for bank {} under OPRF key {}", bank.getId(), customer.getOprfKeyId());
    }

    /**
     * Blocks a customer and suspends the same person wherever else they are
     * enrolled — the cross-institution signal the platform exists to share.
     *
     * <p>The match is on the pseudonym, scoped to the key it was derived under.
     * The server links the records without ever holding the identity that
     * connects them, and a bank learns only that one of its own customers was
     * flagged, never at which other institution or under what name.
     */
    @Transactional
    public void blockCustomer(UUID id) {
        Customer blockedCustomer = customerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(CUSTOMER_NOT_FOUND));

        List<Customer> linkedCustomers = customerRepository.findByPseudonymAndOprfKeyIdAndIdNot(
                blockedCustomer.getPseudonym(), blockedCustomer.getOprfKeyId(), blockedCustomer.getId());

        for (Customer linkedCustomer : linkedCustomers) {
            linkedCustomer.setCustomerStatus(CustomerStatus.SUSPENDED);
        }
        customerRepository.saveAll(linkedCustomers);

        blockedCustomer.setCustomerStatus(CustomerStatus.BLOCKED);
        customerRepository.save(blockedCustomer);

        // Counts only. Logging the pseudonym would put a cross-bank identifier
        // into the log pipeline, which is the one place it is hardest to erase.
        log.info("Customer {} blocked; {} linked record(s) suspended across the network",
                blockedCustomer.getId(), linkedCustomers.size());
    }
}
