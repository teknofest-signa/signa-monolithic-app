package teknofest.signa.producer.service;

import jakarta.mail.internet.MimeMessage;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import teknofest.signa.producer.model.event.NotificationEvent;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;

    @Value("${spring.mail.username}")
    private String from;

    @Value("${application.frontend.base-url}")
    private String frontendBaseUrl;

    public void sendNotification(NotificationEvent notificationEvent) {
        try {
            Map<String, Object> variables = new HashMap<>(notificationEvent.params());
            variables.put("frontendBaseUrl", frontendBaseUrl);

            if (!variables.containsKey("resetUrl") && variables.containsKey("token")) {
                variables.put("resetUrl", frontendBaseUrl + "/admin-register?token=" + variables.get("token"));
            }

            Context context = new Context();
            context.setVariables(variables);

            String htmlContent = templateEngine.process(notificationEvent.type().getTemplateName(), context);

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(from);
            helper.setTo(notificationEvent.to());
            helper.setSubject(notificationEvent.type().getSubject());
            helper.setText(htmlContent, true);

            mailSender.send(message);
            log.info("Email [{}] successfully sent to {}", notificationEvent.type(), notificationEvent.to());

        } catch (Exception e) {
            log.error("Failed to send email [{}] to {}", notificationEvent.type(), notificationEvent.to(), e);
        }
    }
}
