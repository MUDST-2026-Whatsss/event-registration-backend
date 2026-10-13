package MUDST_2026_Whatsss.event_registration.event.domain;

import MUDST_2026_Whatsss.event_registration.auth.domain.AuthUser;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "event_invitations")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class EventInvitation {
    @Id @GeneratedValue
    @Column(name = "invitation_id", nullable = false, updatable = false)
    private UUID invitationId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AuthUser user;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    @Column(name = "accepted_at")
    private Instant acceptedAt;
}
