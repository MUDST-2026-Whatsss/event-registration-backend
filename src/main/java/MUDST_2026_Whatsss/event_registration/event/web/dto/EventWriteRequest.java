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
import jakarta.validation.groups.Default;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import java.util.List;

/** Shared validated shape for the upcoming admin create/update endpoints. */
public record EventWriteRequest(
        @NotBlank(groups = {Default.class, Draft.class}) @Size(max = 255, groups = {Default.class, Draft.class}) String title,
        @Size(max = 500, groups = {Default.class, Draft.class}) String summary,
        String description,
        @NotNull UUID eventCategoryId,
        @NotNull(groups = {Default.class, Draft.class}) EventType eventType,
        @NotNull @DecimalMin(value = "0.00", groups = {Default.class, Draft.class}) BigDecimal price,
        @NotBlank(groups = {Default.class, Draft.class}) @Pattern(regexp = "^[A-Z]{3}$", groups = {Default.class, Draft.class}) String currency,
        String refundPolicy,
        @NotNull(groups = {Default.class, Draft.class}) LocationType locationType,
        @Size(max = 255, groups = {Default.class, Draft.class}) String locationName,
        String address,
        String onlineUrl,
        @Pattern(regexp = EventImageService.OBJECT_KEY_REGEX, groups = {Default.class, Draft.class}) String imageObjectKey,
        @NotBlank(groups = {Default.class, Draft.class}) @Size(max = 64, groups = {Default.class, Draft.class}) String timezone,
        @NotNull Instant startAt,
        @NotNull Instant endAt,
        @NotNull Instant registrationStartAt,
        @NotNull Instant registrationEndAt,
        Instant cancellationDeadlineAt,
        @NotNull @Min(value = 1, groups = {Default.class, Draft.class}) Integer maximumParticipants,
        String rules,
        @Email(groups = {Default.class, Draft.class}) @Size(max = 320, groups = {Default.class, Draft.class}) String contactEmail,
        String eligibility,
        @NotNull(groups = {Default.class, Draft.class}) Boolean allowCancellation,
        @NotNull(groups = {Default.class, Draft.class}) Boolean showRemainingSeats,
        @Size(max = 100, groups = {Default.class, Draft.class}) List<@NotNull(groups = {Default.class, Draft.class}) UUID> invitedUserIds) {

    // Preserve existing callers and leave invitations unchanged when older clients omit them.
    public EventWriteRequest(String title, String summary, String description, UUID eventCategoryId,
            EventType eventType, BigDecimal price, String currency, String refundPolicy,
            LocationType locationType, String locationName, String address, String onlineUrl,
            String imageObjectKey, String timezone, Instant startAt, Instant endAt,
            Instant registrationStartAt, Instant registrationEndAt, Instant cancellationDeadlineAt,
            Integer maximumParticipants, String rules, String contactEmail, String eligibility,
            Boolean allowCancellation, Boolean showRemainingSeats) {
        this(title, summary, description, eventCategoryId, eventType, price, currency, refundPolicy,
                locationType, locationName, address, onlineUrl, imageObjectKey, timezone, startAt,
                endAt, registrationStartAt, registrationEndAt, cancellationDeadlineAt,
                maximumParticipants, rules, contactEmail, eligibility, allowCancellation,
                showRemainingSeats, null);
    }

    /** Drafts may omit scheduling, capacity, category, venue, and price until review. */
    public interface Draft {}

    @JsonIgnore
    @AssertTrue(message = "endAt must be after startAt", groups = {Default.class, Draft.class})
    public boolean isScheduleValid() {
        return startAt == null || endAt == null || endAt.isAfter(startAt);
    }

    @JsonIgnore
    @AssertTrue(message = "registration window must end after it starts and no later than the event", groups = {Default.class, Draft.class})
    public boolean isRegistrationWindowValid() {
        return registrationStartAt == null || registrationEndAt == null || startAt == null
                || (registrationEndAt.isAfter(registrationStartAt)
                && !registrationEndAt.isAfter(startAt));
    }

    @JsonIgnore
    @AssertTrue(message = "free events require zero price and paid events require a positive price", groups = {Default.class, Draft.class})
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
    @AssertTrue(message = "cancellation deadline must be enabled and no later than the event", groups = {Default.class, Draft.class})
    public boolean isCancellationPolicyValid() {
        if (cancellationDeadlineAt == null) {
            return true;
        }
        return Boolean.TRUE.equals(allowCancellation)
                && (startAt == null || !cancellationDeadlineAt.isAfter(startAt));
    }
}
