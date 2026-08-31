package teknofest.signa.producer.model.event;

import java.util.Map;
import teknofest.signa.producer.enums.NotificationType;

public record NotificationEvent(
        String to,
        NotificationType type,
        Map<String, Object> params) {
}
