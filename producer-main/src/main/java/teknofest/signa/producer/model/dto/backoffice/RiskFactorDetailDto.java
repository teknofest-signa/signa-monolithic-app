package teknofest.signa.producer.model.dto.backoffice;

import java.math.BigDecimal;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class RiskFactorDetailDto {

    String code;
    String title;
    String description;
    String severity;
    BigDecimal riskWeight;
    boolean triggered;
}
