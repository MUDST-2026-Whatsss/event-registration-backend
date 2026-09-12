package MUDST_2026_Whatsss.event_registration.common.error;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.Map;

/**
 * The single error body shape for the whole API.
 *
 * @param code       stable identifier the frontend branches on
 * @param message    human-readable text, safe to show to an end user
 * @param fieldErrors per-field validation messages, present only for {@code VALIDATION_FAILED}
 * @param timestamp  when the error was produced
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiErrorResponse(
        String code,
        String message,
        Map<String, String> fieldErrors,
        Instant timestamp) {

    public static ApiErrorResponse of(ErrorCode code, String message) {
        return new ApiErrorResponse(code.name(), message, null, Instant.now());
    }

    public static ApiErrorResponse validation(String message, Map<String, String> fieldErrors) {
        return new ApiErrorResponse(
                ErrorCode.VALIDATION_FAILED.name(), message, fieldErrors, Instant.now());
    }
}
