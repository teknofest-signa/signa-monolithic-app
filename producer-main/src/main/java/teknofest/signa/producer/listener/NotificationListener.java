package teknofest.signa.producer.listener;

import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import teknofest.signa.producer.model.event.NotificationEvent;
import teknofest.signa.producer.service.EmailService;

@Component
@RequiredArgsConstructor
public class NotificationListener {

    private final EmailService emailService;

    @EventListener
    public void handleCreateAdminEvent(NotificationEvent notificationEvent) {
        emailService.sendNotification(notificationEvent);
    }
}
