package MUDST_2026_Whatsss.event_registration.common.error;

import org.springframework.http.HttpStatus;

/**
 * Stable, machine-readable error codes returned to clients.
 *
 * <p>The frontend branches on these strings, so treat them as part of the API contract: add new
 * constants rather than renaming existing ones. Messages are deliberately vague where a precise
 * one would reveal whether an account exists.
 */
public enum ErrorCode {

    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "The submitted data is invalid."),
    EMAIL_ALREADY_REGISTERED(HttpStatus.CONFLICT, "That email address cannot be registered."),

    /** Deliberately identical for unknown email and wrong password. */
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "Incorrect email or password."),
    ACCOUNT_LOCKED(HttpStatus.LOCKED, "Too many failed attempts. Try again later."),
    ACCOUNT_NOT_ACTIVE(HttpStatus.FORBIDDEN, "This account is not active."),

    UNAUTHENTICATED(HttpStatus.UNAUTHORIZED, "Authentication is required."),
    ACCESS_DENIED(HttpStatus.FORBIDDEN, "You do not have permission to perform this action."),
    CSRF_TOKEN_INVALID(HttpStatus.FORBIDDEN, "Request security token is missing or invalid."),

    INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "The session is no longer valid."),
    SESSION_REVOKED(HttpStatus.UNAUTHORIZED, "The session was revoked. Sign in again."),

    INVALID_CURRENT_PASSWORD(HttpStatus.BAD_REQUEST, "The current password is incorrect."),

    EVENT_NOT_FOUND(HttpStatus.NOT_FOUND, "The requested event was not found."),
    EVENT_CATEGORY_NOT_FOUND(HttpStatus.NOT_FOUND, "The requested event category was not found."),
    INVALID_EVENT_STATE(HttpStatus.CONFLICT, "The event cannot perform that transition in its current state."),
    EVENT_VERSION_CONFLICT(HttpStatus.CONFLICT, "The event changed while it was being edited. Reload and try again."),
    EVENT_REVIEW_NOT_FOUND(HttpStatus.NOT_FOUND, "The requested event review was not found."),
    CHANGE_REQUEST_NOT_FOUND(HttpStatus.NOT_FOUND, "The requested change request was not found."),
    ADMIN_USER_NOT_FOUND(HttpStatus.NOT_FOUND, "The requested event administrator was not found."),
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "The requested user was not found."),
    ROLE_NOT_FOUND(HttpStatus.NOT_FOUND, "One or more requested roles were not found."),
    ROLE_CODE_ALREADY_EXISTS(HttpStatus.CONFLICT, "That role code is already in use."),
    PERMISSION_NOT_FOUND(HttpStatus.NOT_FOUND, "One or more requested permissions were not found."),
    USER_VERSION_CONFLICT(HttpStatus.CONFLICT, "The user changed while it was being edited. Reload and try again."),
    INVALID_USER_STATE(HttpStatus.CONFLICT, "The requested account change is not allowed."),

    INVALID_IMAGE_FILE(HttpStatus.BAD_REQUEST, "The uploaded file is not a supported image."),
    MEDIA_NOT_FOUND(HttpStatus.NOT_FOUND, "The requested media was not found."),
    STORAGE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "Image storage is temporarily unavailable."),

    RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "Too many requests. Slow down and try again."),
    PAYLOAD_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE, "The request body is too large."),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "The request method is not allowed."),

    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Something went wrong.");

    private final HttpStatus status;
    private final String defaultMessage;

    ErrorCode(HttpStatus status, String defaultMessage) {
        this.status = status;
        this.defaultMessage = defaultMessage;
    }

    public HttpStatus status() {
        return status;
    }

    public String defaultMessage() {
        return defaultMessage;
    }
}
