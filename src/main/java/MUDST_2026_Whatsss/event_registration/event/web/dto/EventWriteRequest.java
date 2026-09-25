package MUDST_2026_Whatsss.event_registration.event.web.dto;

import MUDST_2026_Whatsss.event_registration.event.domain.EventType;
import MUDST_2026_Whatsss.event_registration.event.domain.LocationType;
import MUDST_2026_Whatsss.event_registration.storage.service.EventImageService;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Shared validated shape for the upcoming admin create/update endpoints. */
public record EventWriteRequest(
        @NotBlank @Size(max = 255) String title,
        @Size(max = 500) String summary,
        String description,
        @NotNull UUID eventCategoryId,
        @NotNull EventType eventType,
        @NotNull @DecimalMin("0.00") BigDecimal price,
        @NotBlank @Pattern(regexp = "^[A-Z]{3}$") String currency,
        String refundPolicy,
        @NotNull LocationType locationType,
        @Size(max = 255) String locationName,
        String address,
        String onlineUrl,
        @Pattern(regexp = EventImageService.OBJECT_KEY_REGEX) String imageObjectKey,
        @NotBlank @Size(max = 64) String timezone,
        @NotNull Instant startAt,
        @NotNull Instant endAt,
        @NotNull Instant registrationStartAt,
        @NotNull Instant registrationEndAt,
        Instant cancellationDeadlineAt,
        @Min(1) int maximumParticipants,
        String rules,
        @Email @Size(max = 320) String contactEmail,
        String eligibility,
        @NotNull Boolean allowCancellation,
        @NotNull Boolean showRemainingSeats) {

    @JsonIgnore
    @AssertTrue(message = "endAt must be after startAt")
    public boolean isScheduleValid() {
        return startAt == null || endAt == null || endAt.isAfter(startAt);
    }

    @JsonIgnore
    @AssertTrue(message = "registration window must end after it starts and no later than the event")
    public boolean isRegistrationWindowValid() {
        return registrationStartAt == null || registrationEndAt == null || startAt == null
                || (registrationEndAt.isAfter(registrationStartAt)
                && !registrationEndAt.isAfter(startAt));
    }

    @JsonIgnore
    @AssertTrue(message = "free events require zero price and paid events require a positive price")
    public boolean isPriceValid() {
        if (eventType == null || price == null) {
            return true;
        }
        return eventType == EventType.FREE
                ? price.compareTo(BigDecimal.ZERO) == 0
                : price.compareTo(BigDecimal.ZERO) > 0;
    }

    @JsonIgnore
    @AssertTrue(message = "location fields do not match locationType")
    public boolean isLocationValid() {
        if (locationType == null) {
            return true;
        }
        boolean hasLocation = locationName != null && !locationName.isBlank();
        boolean hasOnlineUrl = onlineUrl != null && !onlineUrl.isBlank();
        return switch (locationType) {
            case ONSITE -> hasLocation;
            case ONLINE -> hasOnlineUrl;
            case HYBRID -> hasLocation && hasOnlineUrl;
        };
    }

    @JsonIgnore
    @AssertTrue(message = "cancellation deadline must be enabled and no later than the event")
    public boolean isCancellationPolicyValid() {
        if (cancellationDeadlineAt == null) {
            return true;
        }
        return Boolean.TRUE.equals(allowCancellation)
                && (startAt == null || !cancellationDeadlineAt.isAfter(startAt));
    }
}
