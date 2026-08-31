package teknofest.signa.producer.model.dto.oprf;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;
import teknofest.signa.producer.crypto.PiiNormalizer;

/**
 * Development-only request carrying a raw identifier, for the local reference
 * client. Never sent by a real bank connector: a real connector blinds the
 * identifier before anything leaves its perimeter.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class LocalDeriveRequest {

    @NotNull(message = "Identifier type is required!")
    PiiNormalizer.IdentifierType identifierType;

    @NotBlank(message = "Identifier cannot be empty!")
    @Size(max = 128, message = "Identifier is too long!")
    String identifier;
}
