package MUDST_2026_Whatsss.event_registration.event.domain;

import MUDST_2026_Whatsss.event_registration.auth.domain.AuthUser;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Current persistence model for the PostgreSQL {@code events} table. */
@Entity
@Table(name = "events")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Event {

    @Id
    @GeneratedValue
    @Column(name = "event_id", nullable = false, updatable = false)
    private UUID eventId;

    @Column(name = "slug", nullable = false, length = 160, unique = true)
    private String slug;

    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @Column(name = "summary", length = 500)
    private String summary;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_category_id")
    private EventCategory category;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by_user_id", nullable = false, updatable = false)
    private AuthUser createdBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    @Builder.Default
    private EventStatus status = EventStatus.DRAFT;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 16)
    @Builder.Default
    private EventType eventType = EventType.FREE;

    @Column(name = "price", nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal price = BigDecimal.ZERO;

    @Column(name = "currency", nullable = false, length = 3, columnDefinition = "char(3)")
    @JdbcTypeCode(SqlTypes.CHAR)
    @Builder.Default
    private String currency = "THB";

    @Column(name = "refund_policy", columnDefinition = "text")
    private String refundPolicy;

    @Enumerated(EnumType.STRING)
    @Column(name = "location_type", nullable = false, length = 16)
    @Builder.Default
    private LocationType locationType = LocationType.ONSITE;

    @Column(name = "location_name", length = 255)
    private String locationName;

    @Column(name = "address", columnDefinition = "text")
    private String address;

    @Column(name = "online_url", columnDefinition = "text")
    private String onlineUrl;

    @Column(name = "image_url", columnDefinition = "text")
    private String imageObjectKey;

    @Column(name = "timezone", nullable = false, length = 64)
    @Builder.Default
    private String timezone = "Asia/Bangkok";

    @Column(name = "start_at", nullable = false)
    private Instant startAt;

    @Column(name = "end_at", nullable = false)
    private Instant endAt;

    @Column(name = "registration_start_at", nullable = false)
    private Instant registrationStartAt;

    @Column(name = "registration_end_at", nullable = false)
    private Instant registrationEndAt;

    @Column(name = "cancellation_deadline_at")
    private Instant cancellationDeadlineAt;

    @Column(name = "maximum_participants", nullable = false)
    private int maximumParticipants;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "cancellation_reason", length = 500)
    private String cancellationReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @Column(name = "rules", columnDefinition = "text")
    private String rules;

    @Column(name = "contact_email", length = 320)
    private String contactEmail;

    @Column(name = "eligibility", columnDefinition = "text")
    private String eligibility;

    @Column(name = "allow_cancellation", nullable = false)
    @Builder.Default
    private boolean allowCancellation = true;

    @Column(name = "show_remaining_seats", nullable = false)
    @Builder.Default
    private boolean showRemainingSeats = true;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
