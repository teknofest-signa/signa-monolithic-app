package teknofest.signa.producer.repository;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import teknofest.signa.producer.enums.Status;
import teknofest.signa.producer.model.entity.Admin;

@Repository
public interface AdminRepository extends JpaRepository<Admin, UUID> {

    boolean existsByEmail(String email);
    boolean existsByUsername(String username);

    Optional<Admin> findByEmail(String email);
    Optional<Admin> findByEmailAndStatus(String email, Status status);
    Optional<Admin> findByTokenAndStatus(String token, Status status);

    Optional<Admin> findByResetPasswordToken(String resetPasswordToken);
}
