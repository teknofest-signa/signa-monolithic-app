package teknofest.signa.producer.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
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
}
