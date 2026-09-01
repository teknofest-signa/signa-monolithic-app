package teknofest.signa.producer.model.dto.screening;

import java.time.Instant;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;
import teknofest.signa.producer.enums.ScreeningStatus;

/**
 * The verdict, and as little else as the question allows.
 *
 * <p>What is missing here is the point. There is no institution name, no
 * customer name, no count of how many banks flagged the person and no date of
 * the first flag. Each of those is a step towards reconstructing another
 * institution's customer book from the outside, and the platform's claim is
 * that a bank learns that its counterparty is flagged and nothing further.
 *
 * <p>{@link #enrolledWithYou} is the one exception, and it is not an exception
 * at all: it reports the caller's own enrolment, which the caller already
 * holds. It is here so a bank can tell "flagged, and already my customer" from
 * "flagged, and walking in for the first time" without a second query.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ScreeningResponse {

    ScreeningStatus status;

    /** Echoed back so the caller can confirm which key epoch answered. */
    String oprfKeyId;

    /** Whether the calling bank has this person enrolled itself. */
    boolean enrolledWithYou;

    Instant checkedAt;
}
