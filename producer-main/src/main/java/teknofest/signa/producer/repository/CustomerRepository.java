package teknofest.signa.producer.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import teknofest.signa.producer.enums.CustomerStatus;
import teknofest.signa.producer.model.entity.Customer;

@Repository
public interface CustomerRepository extends JpaRepository<Customer, UUID> {
    /**
     * Every other enrolment of the same person, scoped to the OPRF key the
     * pseudonym was derived under. Pseudonyms from different keys are
     * incomparable, so leaving the key out would quietly return nothing.
     */
    List<Customer> findByPseudonymAndOprfKeyIdAndIdNot(String pseudonym, String oprfKeyId, UUID id);

    boolean existsByBankIdAndPseudonymAndOprfKeyId(UUID bankId, String pseudonym, String oprfKeyId);

    /**
     * Whether anyone in the network holds this person in one of the given
     * statuses. This is the whole of a screening check: it answers yes or no
     * across every member institution without the caller, or this method,
     * naming any of them.
     *
     * <p>Covered by {@code idx_customers_pseudonym_key}, and it returns a
     * boolean rather than the rows so that a verdict cannot accidentally be
     * built from data the caller is not entitled to see.
     */
    boolean existsByPseudonymAndOprfKeyIdAndCustomerStatusIn(
            String pseudonym, String oprfKeyId, List<CustomerStatus> statuses);
}
