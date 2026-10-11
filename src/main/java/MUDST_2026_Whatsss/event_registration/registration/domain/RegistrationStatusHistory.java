package MUDST_2026_Whatsss.event_registration.registration.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** Append-only lifecycle trail for a registration, in {@code registration_status_history}. */
@Entity
@Table(name = "registration_status_history")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RegistrationStatusHistory {

    @Id
    @GeneratedValue
    @Column(name = "registration_status_history_id", nullable = false, updatable = false)
    private UUID registrationStatusHistoryId;

    @Column(name = "registration_id", nullable = false, updatable = false)
    private UUID registrationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", length = 30, updatable = false)
    private RegistrationStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false, length = 30, updatable = false)
    private RegistrationStatus toStatus;

    @Column(name = "changed_by_user_id", updatable = false)
    private UUID changedByUserId;

    @Column(name = "reason", columnDefinition = "text", updatable = false)
    private String reason;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", nullable = false, columnDefinition = "jsonb", updatable = false)
    @Builder.Default
    private Map<String, Object> metadata = Map.of();

    @Column(name = "changed_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant changedAt = Instant.now();
}
