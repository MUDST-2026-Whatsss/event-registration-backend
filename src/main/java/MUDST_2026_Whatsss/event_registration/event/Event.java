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
