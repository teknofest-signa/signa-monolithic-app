package teknofest.signa.producer.constants;

public final class ErrorConstants {

    private ErrorConstants() {
    }

    public static final String BANK_NOT_FOUND = "BANK NOT FOUND!";
    public static final String ADMIN_NOT_FOUND = "ADMIN NOT FOUND!";
    public static final String TOKEN_NOT_FOUND = "TOKEN NOT FOUND!";
    public static final String CUSTOMER_NOT_FOUND = "CUSTOMER NOT FOUND!";

    public static final String EMAIL_ALREADY_EXISTS = "EMAIL ALREADY EXISTS!";
    public static final String USERNAME_ALREADY_EXISTS = "USERNAME ALREADY EXISTS!";

    public static final String FAILED_TO_UPLOAD_PHOTO = "FAILED TO UPLOAD PHOTO!";

    public static final String PASSWORD_EXPIRED = "PASSWORD RESET TOKEN HAS EXPIRED!";
    public static final String PASSWORD_COOLDOWN_EXCEPTION = "WAIT BEFORE REQUESTING A NEW PASSWORD RESET LINK!";

    public static final String UNKNOWN_OPRF_KEY = "UNKNOWN OPRF KEY ID!";
    public static final String CUSTOMER_ALREADY_ENROLLED = "CUSTOMER IS ALREADY ENROLLED FOR THIS BANK!";
    public static final String OPRF_CREDENTIALS_REQUIRED = "MEMBER BANK CREDENTIALS ARE REQUIRED!";
}

