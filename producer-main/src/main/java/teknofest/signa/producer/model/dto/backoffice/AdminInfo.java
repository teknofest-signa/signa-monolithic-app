package teknofest.signa.producer.model.dto.backoffice;

import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;
import teknofest.signa.producer.enums.Role;
import teknofest.signa.producer.enums.Status;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class AdminInfo {
    UUID id;

    String email;
    String username;

    Status status;

    Role role;

    byte[] profilePhoto;

    Instant createdAt;
    Instant updatedAt;
}
