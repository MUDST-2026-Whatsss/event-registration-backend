package MUDST_2026_Whatsss.event_registration.event;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * TEMPORARILY NOT REGISTERED.
 *
 * <p>These fields still describe the pre-V6 events table: a {@code Long id}, {@code event_date} and
 * {@code max_participants}, where the table now has {@code event_id uuid}, {@code start_at} and
 * {@code maximum_participants}. Under {@code ddl-auto=validate} that mismatch fails schema
 * validation at startup and takes the whole application down, authentication included.
 *
 * <p>The mapping is left intact but kept out of the persistence unit by the {@code @EntityScan} in
 * {@code EventRegistrationApplication}, which covers only the auth package. To bring the events API
 * back: remap the fields below to the V6 columns (the id becomes a {@code UUID}), then remove the
 * scan restrictions in {@code EventRegistrationApplication} and the test exclusions in
 * {@code pom.xml}.
 */
@Entity
@Table(name = "events")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Event {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    private String category;

    @Column(columnDefinition = "TEXT")
    private String location;

    private LocalDate eventDate;

    private LocalTime startTime;

    private LocalTime endTime;

    private Integer maxParticipants;

    private LocalDateTime registrationDeadline;

    @Column(columnDefinition = "TEXT")
    private String image;

    @Column(columnDefinition = "TEXT")
    private String rules;

    private String contactEmail;

    @Column(columnDefinition = "TEXT")
    private String eligibility;

    @Builder.Default
    private Boolean allowCancel = true;

    @Builder.Default
    private Boolean showSeats = true;

    @Builder.Default
    private String status = "draft";

    private Integer createdBy;

    private LocalDateTime createdAt;
}
