package MUDST_2026_Whatsss.event_registration.common.error;

import lombok.Getter;

/** An application error that maps to a known {@link ErrorCode} and HTTP status. */
@Getter
public class ApiException extends RuntimeException {

    private final ErrorCode errorCode;

    public ApiException(ErrorCode errorCode) {
        this(errorCode, errorCode.defaultMessage());
    }

    public ApiException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
}
