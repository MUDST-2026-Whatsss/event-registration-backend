package MUDST_2026_Whatsss.event_registration.registration.service;

import MUDST_2026_Whatsss.event_registration.auth.domain.Participant;
import MUDST_2026_Whatsss.event_registration.auth.repository.ParticipantRepository;
import MUDST_2026_Whatsss.event_registration.auth.security.AuthenticatedUser;
import MUDST_2026_Whatsss.event_registration.auth.security.RoleCodes;
import MUDST_2026_Whatsss.event_registration.common.error.ApiException;
import MUDST_2026_Whatsss.event_registration.common.error.ErrorCode;
import MUDST_2026_Whatsss.event_registration.event.domain.Event;
import MUDST_2026_Whatsss.event_registration.event.domain.EventAdminAssignment;
import MUDST_2026_Whatsss.event_registration.event.domain.EventStatus;
import MUDST_2026_Whatsss.event_registration.event.domain.EventType;
import MUDST_2026_Whatsss.event_registration.event.repository.EventAdminAssignmentRepository;
import MUDST_2026_Whatsss.event_registration.event.repository.EventRepository;
import MUDST_2026_Whatsss.event_registration.event.repository.EventInvitationRepository;
import MUDST_2026_Whatsss.event_registration.registration.web.dto.EventInvitationResponse;
import MUDST_2026_Whatsss.event_registration.event.service.EventMapper;
import MUDST_2026_Whatsss.event_registration.event.service.EventNotFoundException;
import MUDST_2026_Whatsss.event_registration.registration.domain.Registration;
import MUDST_2026_Whatsss.event_registration.registration.domain.RegistrationStatus;
import MUDST_2026_Whatsss.event_registration.registration.domain.RegistrationStatusHistory;
import MUDST_2026_Whatsss.event_registration.registration.repository.RegistrationRepository;
import MUDST_2026_Whatsss.event_registration.registration.repository.RegistrationStatusHistoryRepository;
import MUDST_2026_Whatsss.event_registration.registration.web.dto.AdminRecentRegistrationResponse;
import MUDST_2026_Whatsss.event_registration.registration.web.dto.AdminRegistrationResponse;
import MUDST_2026_Whatsss.event_registration.registration.web.dto.CheckInRequest;
import MUDST_2026_Whatsss.event_registration.registration.web.dto.CreateRegistrationRequest;
import MUDST_2026_Whatsss.event_registration.registration.web.dto.EventAnalyticsResponse;
import MUDST_2026_Whatsss.event_registration.registration.web.dto.GuestRegistrationRequest;
import MUDST_2026_Whatsss.event_registration.registration.web.dto.GuestTicketLookupRequest;
import MUDST_2026_Whatsss.event_registration.registration.web.dto.RegistrationResponse;
import org.springframework.data.domain.Page;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Participant registration lifecycle and the per-event participant list.
 *
 * <p>Capacity is enforced by locking the event row ({@code SELECT ... FOR UPDATE}) before counting
 * active registrations, so concurrent requests for the same event are serialised and the last seat
 * cannot be sold twice.
 */
@Service
public class RegistrationService {

    private static final String NUMBER_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final RegistrationRepository registrationRepository;
    private final RegistrationStatusHistoryRepository historyRepository;
    private final EventRepository eventRepository;
    private final EventAdminAssignmentRepository assignmentRepository;
    private final ParticipantRepository participantRepository;
    private final EventMapper eventMapper;
    private final EventInvitationRepository invitationRepository;
    private final boolean mockPaymentsEnabled;

    public RegistrationService(
            RegistrationRepository registrationRepository,
            RegistrationStatusHistoryRepository historyRepository,
            EventRepository eventRepository,
            EventAdminAssignmentRepository assignmentRepository,
            ParticipantRepository participantRepository,
            EventMapper eventMapper,
            EventInvitationRepository invitationRepository,
            @Value("${event.registration.mock-payments-enabled:false}") boolean mockPaymentsEnabled) {
        this.registrationRepository = registrationRepository;
        this.historyRepository = historyRepository;
        this.eventRepository = eventRepository;
        this.assignmentRepository = assignmentRepository;
        this.participantRepository = participantRepository;
        this.eventMapper = eventMapper;
        this.invitationRepository = invitationRepository;
        this.mockPaymentsEnabled = mockPaymentsEnabled;
    }

    @Transactional
    public RegistrationResponse register(
            UUID eventId, CreateRegistrationRequest request, AuthenticatedUser principal) {
        Instant now = Instant.now();
        Event event = lockOpenEvent(eventId, now);
        boolean paid = event.getEventType() == EventType.PAID;
        if (paid && !mockPaymentsEnabled) {
            throw new ApiException(ErrorCode.PAID_REGISTRATION_UNAVAILABLE);
        }

        Participant participant = participantFor(principal.userId());
        Registration registration = registrationRepository
                .findByEvent_EventIdAndParticipantId(eventId, participant.getParticipantId())
                .orElse(null);
        if (registration != null && registration.getStatus().isActive()) {
            // A retry resumes the same pending payment instead of consuming another seat.
            if (paid && (registration.getStatus() == RegistrationStatus.PENDING_PAYMENT
                    || registration.getStatus() == RegistrationStatus.CONFIRMED)) {
                return toResponse(registration, now);
            }
            throw new ApiException(ErrorCode.ALREADY_REGISTERED);
        }
        requireSeat(event);

        RegistrationStatus previous = registration == null ? null : registration.getStatus();
        if (registration == null) {
            registration = Registration.builder()
                    .registrationNumber(newRegistrationNumber())
                    .event(event)
                    .participantId(participant.getParticipantId())
                    .build();
        }
        registration.setRegisteredByUserId(principal.userId());
        registration.setStatus(paid ? RegistrationStatus.PENDING_PAYMENT : RegistrationStatus.CONFIRMED);
        registration.setAttendeeName(attendeeName(request, participant, principal));
        registration.setAttendeeEmail(firstNonBlank(request.attendeeEmail(), principal.email()));
        registration.setAttendeePhone(attendeePhone(request, participant));
        registration.setConsentAcceptedAt(now);
        registration.setRegisteredAt(now);
        registration.setConfirmedAt(paid ? null : now);
        registration.setCancelledAt(null);
        registration.setCancellationReason(null);
        registration = registrationRepository.saveAndFlush(registration);

        invitationRepository.findByEvent_EventIdAndUser_UserId(eventId, principal.userId())
                .ifPresent(invitation -> invitation.setAcceptedAt(now));

        recordTransition(registration, previous, principal.userId(), null);
        return toResponse(registration, now);
    }

    /**
     * A visitor registers without an account. Only free events are open to guests, because paying
     * needs a signed-in owner. The email is how the guest finds the ticket again and how it is linked
     * to an account later, so one email holds at most one active seat per event.
     */
    @Transactional
    public RegistrationResponse registerGuest(UUID eventId, GuestRegistrationRequest request) {
        Instant now = Instant.now();
        Event event = lockOpenEvent(eventId, now);
        if (event.getEventType() == EventType.PAID) {
            throw new ApiException(ErrorCode.GUEST_PAID_REGISTRATION_UNAVAILABLE);
        }
        String email = request.attendeeEmail().trim();
        if (registrationRepository.existsGuestRegistration(
                eventId, normalizeEmail(email), RegistrationStatus.ACTIVE)) {
            throw new ApiException(ErrorCode.GUEST_ALREADY_REGISTERED);
        }
        requireSeat(event);

        Registration registration = registrationRepository.saveAndFlush(Registration.builder()
                .registrationNumber(newRegistrationNumber())
                .event(event)
                .registeredAsGuest(true)
                .status(RegistrationStatus.CONFIRMED)
                .attendeeName(request.attendeeName().trim())
                .attendeeEmail(email)
                .attendeePhone(firstNonBlank(request.attendeePhone()))
                .consentAcceptedAt(now)
                .registeredAt(now)
                .confirmedAt(now)
                .build());
        recordTransition(registration, null, null, null);
        return toResponse(registration, now, false);
    }

    /** A guest's ticket, found by the registration number and email they registered with. */
    @Transactional(readOnly = true)
    public RegistrationResponse guestTicket(GuestTicketLookupRequest request) {
        String registrationNumber = request.registrationNumber().trim().toUpperCase(Locale.ROOT);
        // A wrong email is reported like an unknown number, so neither can be probed alone.
        return registrationRepository.findGuestTicket(registrationNumber, normalizeEmail(request.email()))
                .map(registration -> toResponse(registration, Instant.now(), false))
                .orElseThrow(() -> new ApiException(ErrorCode.REGISTRATION_NOT_FOUND));
    }

    /**
     * Moves the guest registrations made with this email into the account's history.
     *
     * <p>Callers must only pass an address the account has verified; otherwise anyone could sign up
     * with someone else's email and read their ticket. A guest row for an event the account already
     * holds a registration for stays unlinked, because a participant has one row per event.
     */
    @Transactional
    public int linkGuestRegistrations(UUID userId, String normalizedEmail) {
        List<Registration> guestRegistrations =
                registrationRepository.findUnlinkedGuestRegistrations(normalizedEmail);
        if (guestRegistrations.isEmpty()) {
            return 0;
        }
        UUID participantId = participantFor(userId).getParticipantId();
        Set<UUID> heldEvents = new HashSet<>();
        Instant now = Instant.now();
        int linked = 0;
        for (Registration registration : guestRegistrations) {
            UUID eventId = registration.getEvent().getEventId();
            if (!heldEvents.add(eventId)
                    || registrationRepository.findByEvent_EventIdAndParticipantId(eventId, participantId).isPresent()) {
                continue;
            }
            registration.setParticipantId(participantId);
            registration.setGuestLinkedAt(now);
            registrationRepository.saveAndFlush(registration);
            linked++;
        }
        return linked;
    }

    /** Demo-only confirmation: never accepts a client-provided payment status or transaction ID. */
    @Transactional
    public RegistrationResponse confirmMockPayment(UUID registrationId, AuthenticatedUser principal) {
        if (!mockPaymentsEnabled) {
            throw new ApiException(ErrorCode.PAID_REGISTRATION_UNAVAILABLE);
        }
        Registration registration = lockedOwned(registrationId, principal);
        Event event = registration.getEvent();
        Instant now = Instant.now();
        if (event.getEventType() != EventType.PAID || event.getStatus() != EventStatus.PUBLISHED) {
            throw new ApiException(ErrorCode.INVALID_REGISTRATION_STATE);
        }
        // Repeated confirmation returns the persisted result without duplicate history/seats.
        if (registration.getStatus() == RegistrationStatus.CONFIRMED) {
            return toResponse(registration, now);
        }
        if (registration.getStatus() != RegistrationStatus.PENDING_PAYMENT) {
            throw new ApiException(ErrorCode.INVALID_REGISTRATION_STATE);
        }
        if (!now.isBefore(event.getRegistrationEndAt())) {
            throw new ApiException(ErrorCode.REGISTRATION_CLOSED);
        }
        registration.setStatus(RegistrationStatus.CONFIRMED);
        registration.setConfirmedAt(now);
        registration = registrationRepository.saveAndFlush(registration);
        recordTransition(registration, RegistrationStatus.PENDING_PAYMENT, principal.userId(),
                "MOCK_PAYMENT_CONFIRMED: no real payment collected");
        return toResponse(registration, now);
    }

    @Transactional(readOnly = true)
    public List<RegistrationResponse> mine(AuthenticatedUser principal) {
        Instant now = Instant.now();
        return participantRepository.findByUserId(principal.userId())
                .map(participant -> registrationRepository.findMine(participant.getParticipantId()))
                .orElse(List.of())
                .stream()
                .map(registration -> toResponse(registration, now))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<EventInvitationResponse> invitations(AuthenticatedUser principal) {
        return invitationRepository.findVisibleForUser(principal.userId(), Instant.now()).stream()
                .map(invitation -> new EventInvitationResponse(invitation.getInvitationId(),
                        eventMapper.toSummaryResponse(invitation.getEvent(),
                                eventRepository.countActiveRegistrations(invitation.getEvent().getEventId()))))
                .toList();
    }

    @Transactional(readOnly = true)
    public RegistrationResponse get(UUID registrationId, AuthenticatedUser principal) {
        return toResponse(owned(registrationId, principal), Instant.now());
    }

    @Transactional
    public RegistrationResponse cancel(UUID registrationId, AuthenticatedUser principal) {
        Registration registration = lockedOwned(registrationId, principal);
        Instant now = Instant.now();
        if (!cancellable(registration, now)) {
            throw new ApiException(ErrorCode.REGISTRATION_NOT_CANCELLABLE);
        }
        RegistrationStatus previous = registration.getStatus();
        registration.setStatus(RegistrationStatus.CANCELLED_BY_USER);
        registration.setCancelledAt(now);
        registration = registrationRepository.saveAndFlush(registration);
        recordTransition(registration, previous, principal.userId(), null);
        return toResponse(registration, now);
    }

    /** Releases every active seat when an administrator cancels the whole event. */
    @Transactional
    public void cancelAllForEvent(Event event, UUID actorUserId, String reason) {
        Instant now = Instant.now();
        for (Registration registration : registrationRepository
                .findByEvent_EventIdAndStatusIn(event.getEventId(), RegistrationStatus.ACTIVE)) {
            RegistrationStatus previous = registration.getStatus();
            registration.setStatus(RegistrationStatus.CANCELLED_EVENT);
            registration.setCancelledAt(now);
            registration.setCancellationReason(reason);
            registrationRepository.save(registration);
            recordTransition(registration, previous, actorUserId, reason);
        }
    }

    /** Marks a confirmed attendee as present, matched by the registration number on their QR code. */
    @Transactional
    public AdminRegistrationResponse checkIn(
            UUID eventId, CheckInRequest request, AuthenticatedUser principal) {
        Event event = eventRepository.findByIdForUpdate(eventId).orElseThrow(EventNotFoundException::new);
        requireEventScope(event, principal);
        if (event.getStatus() != EventStatus.PUBLISHED) {
            throw new ApiException(ErrorCode.INVALID_EVENT_STATE);
        }
        String registrationNumber = request.registrationNumber().trim().toUpperCase();
        // A number from another event is reported as missing, like an unknown number.
        Registration registration = registrationRepository
                .findByEvent_EventIdAndRegistrationNumber(eventId, registrationNumber)
                .orElseThrow(() -> new ApiException(ErrorCode.REGISTRATION_NOT_FOUND));
        if (registration.getStatus() == RegistrationStatus.CHECKED_IN) {
            throw new ApiException(ErrorCode.ALREADY_CHECKED_IN);
        }
        if (registration.getStatus() != RegistrationStatus.CONFIRMED) {
            throw new ApiException(ErrorCode.REGISTRATION_NOT_CHECKABLE);
        }
        Instant now = Instant.now();
        registration.setStatus(RegistrationStatus.CHECKED_IN);
        registration.setCheckedInAt(now);
        registration.setCheckedInByUserId(principal.userId());
        registration = registrationRepository.saveAndFlush(registration);
        recordTransition(registration, RegistrationStatus.CONFIRMED, principal.userId(), null);
        return toAdminResponse(registration);
    }

    @Transactional(readOnly = true)
    public Page<AdminRegistrationResponse> listForEvent(
            UUID eventId, String query, Pageable pageable, AuthenticatedUser principal) {
        Event event = eventRepository.findById(eventId).orElseThrow(EventNotFoundException::new);
        requireEventScope(event, principal);
        String normalizedQuery = query == null ? "" : query.trim();
        return registrationRepository.findForEvent(eventId, normalizedQuery, pageable)
                .map(RegistrationService::toAdminResponse);
    }

    @Transactional(readOnly = true)
    public EventAnalyticsResponse analytics(UUID eventId, AuthenticatedUser principal) {
        Event event = eventRepository.findById(eventId).orElseThrow(EventNotFoundException::new);
        requireEventScope(event, principal);

        Map<RegistrationStatus, Long> byStatus = new EnumMap<>(RegistrationStatus.class);
        long guestSignups = 0;
        for (Object[] row : registrationRepository.countByStatusAndGuest(eventId)) {
            long count = ((Number) row[2]).longValue();
            byStatus.merge(RegistrationStatus.valueOf((String) row[0]), count, Long::sum);
            if ((Boolean) row[1]) {
                guestSignups += count;
            }
        }
        long signups = byStatus.values().stream().mapToLong(Long::longValue).sum();
        long dropOffs = byStatus.getOrDefault(RegistrationStatus.CANCELLED_BY_USER, 0L)
                + byStatus.getOrDefault(RegistrationStatus.PAYMENT_EXPIRED, 0L);
        long checkedIn = byStatus.getOrDefault(RegistrationStatus.CHECKED_IN, 0L);
        long noShows = byStatus.getOrDefault(RegistrationStatus.NO_SHOW, 0L);
        // Before the end, unchecked attendees may still arrive, so a show rate would mislead.
        boolean ended = event.getEndAt() != null && !Instant.now().isBefore(event.getEndAt());

        List<EventAnalyticsResponse.DailySignups> daily = registrationRepository
                .countDailySignups(eventId, event.getTimezone()).stream()
                .map(row -> new EventAnalyticsResponse.DailySignups(
                        LocalDate.parse((String) row[0]), ((Number) row[1]).longValue()))
                .toList();
        return new EventAnalyticsResponse(
                signups,
                signups - guestSignups,
                guestSignups,
                dropOffs,
                ratio(dropOffs, signups),
                byStatus.getOrDefault(RegistrationStatus.CANCELLED_BY_ADMIN, 0L),
                byStatus.getOrDefault(RegistrationStatus.CONFIRMED, 0L),
                checkedIn,
                noShows,
                ended ? ratio(checkedIn, checkedIn + noShows) : null,
                ended,
                daily);
    }

    @Transactional(readOnly = true)
    public Page<AdminRecentRegistrationResponse> listRecent(
            Pageable pageable, AuthenticatedUser principal) {
        Page<Registration> registrations = principal.hasRole(RoleCodes.SUPER_ADMIN)
                ? registrationRepository.findRecent(pageable)
                : registrationRepository.findRecentForAssignedEvents(principal.userId(), pageable);
        return registrations.map(RegistrationService::toRecentResponse);
    }

    private Registration owned(UUID registrationId, AuthenticatedUser principal) {
        UUID participantId = participantRepository.findByUserId(principal.userId())
                .map(Participant::getParticipantId)
                .orElseThrow(() -> new ApiException(ErrorCode.REGISTRATION_NOT_FOUND));
        // Someone else's registration is reported as missing so IDs cannot be probed.
        return registrationRepository.findDetailById(registrationId)
                .filter(registration -> participantId.equals(registration.getParticipantId()))
                .orElseThrow(() -> new ApiException(ErrorCode.REGISTRATION_NOT_FOUND));
    }

    private Registration lockedOwned(UUID registrationId, AuthenticatedUser principal) {
        UUID eventId = registrationRepository.findOwnedEventId(registrationId, principal.userId())
                .orElseThrow(() -> new ApiException(ErrorCode.REGISTRATION_NOT_FOUND));
        eventRepository.findByIdForUpdate(eventId).orElseThrow(EventNotFoundException::new);
        return owned(registrationId, principal);
    }

    /** Accounts created before participant profiles existed get one on first registration. */
    private Participant participantFor(UUID userId) {
        return participantRepository.findByUserId(userId)
                .orElseGet(() -> participantRepository.save(Participant.builder()
                        .userId(userId)
                        .build()));
    }

    /** Locks the event row so the seat count read by {@link #requireSeat} cannot change underneath. */
    private Event lockOpenEvent(UUID eventId, Instant now) {
        Event event = eventRepository.findByIdForUpdate(eventId)
                .filter(candidate -> candidate.getStatus() == EventStatus.PUBLISHED)
                .orElseThrow(EventNotFoundException::new);
        if (now.isBefore(event.getRegistrationStartAt()) || !now.isBefore(event.getRegistrationEndAt())) {
            throw new ApiException(ErrorCode.REGISTRATION_CLOSED);
        }
        return event;
    }

    private void requireSeat(Event event) {
        if (eventRepository.countActiveRegistrations(event.getEventId()) >= event.getMaximumParticipants()) {
            throw new ApiException(ErrorCode.EVENT_FULL);
        }
    }

    private void requireEventScope(Event event, AuthenticatedUser principal) {
        if (principal.hasRole(RoleCodes.SUPER_ADMIN)) {
            return;
        }
        boolean assigned = assignmentRepository.existsByEvent_EventIdAndAdminUser_UserIdAndStatus(
                event.getEventId(), principal.userId(), EventAdminAssignment.STATUS_ACTIVE);
        if (!assigned) {
            throw new EventNotFoundException();
        }
    }

    private static boolean cancellable(Registration registration, Instant now) {
        Event event = registration.getEvent();
        boolean seatHeld = registration.getStatus() == RegistrationStatus.CONFIRMED
                || registration.getStatus() == RegistrationStatus.PENDING_PAYMENT;
        return seatHeld
                && event.getStatus() == EventStatus.PUBLISHED
                && (registration.getStatus() == RegistrationStatus.PENDING_PAYMENT || event.isAllowCancellation())
                && now.isBefore(event.getStartAt())
                && (event.getCancellationDeadlineAt() == null
                        || now.isBefore(event.getCancellationDeadlineAt()));
    }

    private void recordTransition(
            Registration registration, RegistrationStatus from, UUID actorUserId, String reason) {
        historyRepository.save(RegistrationStatusHistory.builder()
                .registrationId(registration.getRegistrationId())
                .fromStatus(from)
                .toStatus(registration.getStatus())
                .changedByUserId(actorUserId)
                .reason(reason)
                .metadata(Map.of())
                .build());
    }

    private RegistrationResponse toResponse(Registration registration, Instant now) {
        return toResponse(registration, now, cancellable(registration, now));
    }

    /** Guests read their ticket without an owner, so they are never offered cancellation. */
    private RegistrationResponse toResponse(Registration registration, Instant now, boolean cancellable) {
        Event event = registration.getEvent();
        return new RegistrationResponse(
                registration.getRegistrationId(),
                registration.getRegistrationNumber(),
                registration.getStatus(),
                registration.getAttendeeName(),
                registration.getAttendeeEmail(),
                registration.getAttendeePhone(),
                registration.getRegisteredAt(),
                registration.getConfirmedAt(),
                registration.getCancelledAt(),
                registration.getCancellationReason(),
                cancellable,
                registration.isRegisteredAsGuest(),
                eventMapper.toSummaryResponse(
                        event, eventRepository.countActiveRegistrations(event.getEventId())));
    }

    private static Double ratio(long part, long whole) {
        return whole == 0 ? null : (double) part / whole;
    }

    private static AdminRegistrationResponse toAdminResponse(Registration registration) {
        return new AdminRegistrationResponse(
                registration.getRegistrationId(),
                registration.getRegistrationNumber(),
                registration.getStatus(),
                registration.getAttendeeName(),
                registration.getAttendeeEmail(),
                registration.getAttendeePhone(),
                registration.getRegisteredAt(),
                registration.getConfirmedAt(),
                registration.getCancelledAt(),
                registration.getCancellationReason(),
                registration.getCheckedInAt(),
                registration.isRegisteredAsGuest());
    }

    private static AdminRecentRegistrationResponse toRecentResponse(Registration registration) {
        Event event = registration.getEvent();
        return new AdminRecentRegistrationResponse(
                registration.getRegistrationId(),
                event.getEventId(),
                event.getTitle(),
                registration.getRegistrationNumber(),
                registration.getStatus(),
                registration.getAttendeeName(),
                registration.getAttendeeEmail(),
                registration.getRegisteredAt());
    }

    private String newRegistrationNumber() {
        String number;
        do {
            StringBuilder builder = new StringBuilder("REG-");
            for (int i = 0; i < 10; i++) {
                builder.append(NUMBER_ALPHABET.charAt(RANDOM.nextInt(NUMBER_ALPHABET.length())));
            }
            number = builder.toString();
        } while (registrationRepository.existsByRegistrationNumber(number));
        return number;
    }

    private static String attendeeName(
            CreateRegistrationRequest request, Participant participant, AuthenticatedUser principal) {
        String profileName = String.join(" ",
                participant.getFirstName() == null ? "" : participant.getFirstName().trim(),
                participant.getLastName() == null ? "" : participant.getLastName().trim()).trim();
        return firstNonBlank(request.attendeeName(), profileName, principal.email());
    }

    private static String attendeePhone(CreateRegistrationRequest request, Participant participant) {
        String phone = firstNonBlank(request.attendeePhone(), participant.getPhoneNumber());
        // The profile column allows 32 characters but the registration snapshot only 30.
        return phone == null || phone.length() > 30 ? null : phone;
    }

    /** Same rule as account emails, so a guest email matches the account that signs up with it. */
    private static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }
}
