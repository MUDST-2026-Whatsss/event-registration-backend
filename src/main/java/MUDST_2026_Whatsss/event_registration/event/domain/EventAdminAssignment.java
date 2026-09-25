package MUDST_2026_Whatsss.event_registration.event.domain;

import MUDST_2026_Whatsss.event_registration.auth.domain.AuthUser;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "event_admin_assignments")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EventAdminAssignment {

    public static final String ROLE_OWNER = "OWNER";
    public static final String STATUS_ACTIVE = "ACTIVE";

    @Id
    @GeneratedValue
    @Column(name = "event_admin_assignment_id", nullable = false, updatable = false)
    private UUID assignmentId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "admin_user_id", nullable = false)
    private AuthUser adminUser;

    @Column(name = "assignment_role", nullable = false, length = 30)
    @Builder.Default
    private String assignmentRole = ROLE_OWNER;

    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private String status = STATUS_ACTIVE;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_by_user_id")
    private AuthUser assignedBy;

    @Column(name = "assigned_at", nullable = false)
    @Builder.Default
    private Instant assignedAt = Instant.now();

    @Column(name = "removed_at")
    private Instant removedAt;
}
