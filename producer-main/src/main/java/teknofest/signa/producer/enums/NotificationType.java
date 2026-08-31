package teknofest.signa.producer.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum NotificationType {
    CREATE_ADMIN("create-admin", "Welcome to SiGNA!"),
    PASSWORD_RESET("password-reset", "Your password reset link!"),;

    private final String templateName;
    private final String subject;
}
