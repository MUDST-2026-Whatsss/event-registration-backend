package MUDST_2026_Whatsss.event_registration.event.service;

import MUDST_2026_Whatsss.event_registration.auth.domain.AuthUser;
import MUDST_2026_Whatsss.event_registration.auth.repository.AuthUserRepository;
import MUDST_2026_Whatsss.event_registration.auth.security.AuthenticatedUser;
import MUDST_2026_Whatsss.event_registration.auth.security.RoleCodes;
import MUDST_2026_Whatsss.event_registration.common.error.ApiException;
import MUDST_2026_Whatsss.event_registration.common.error.ErrorCode;
import MUDST_2026_Whatsss.event_registration.event.domain.AuditLog;
import MUDST_2026_Whatsss.event_registration.event.domain.Event;
import MUDST_2026_Whatsss.event_registration.event.domain.EventAdminAssignment;
import MUDST_2026_Whatsss.event_registration.event.domain.EventCategory;
import MUDST_2026_Whatsss.event_registration.event.domain.EventChangeRequest;
import MUDST_2026_Whatsss.event_registration.event.domain.EventChangeRequestItem;
import MUDST_2026_Whatsss.event_registration.event.domain.EventReview;
import MUDST_2026_Whatsss.event_registration.event.domain.EventStatus;
import MUDST_2026_Whatsss.event_registration.event.repository.AuditLogRepository;
import MUDST_2026_Whatsss.event_registration.event.repository.EventAdminAssignmentRepository;
import MUDST_2026_Whatsss.event_registration.event.repository.EventCategoryRepository;
import MUDST_2026_Whatsss.event_registration.event.repository.EventChangeRequestItemRepository;
import MUDST_2026_Whatsss.event_registration.event.repository.EventChangeRequestRepository;
import MUDST_2026_Whatsss.event_registration.event.repository.EventRepository;
import MUDST_2026_Whatsss.event_registration.event.repository.EventReviewRepository;
import MUDST_2026_Whatsss.event_registration.event.web.dto.AdminEventResponse;
import MUDST_2026_Whatsss.event_registration.event.web.dto.AdminEventStatsResponse;
import MUDST_2026_Whatsss.event_registration.event.web.dto.CancelEventRequest;
import MUDST_2026_Whatsss.event_registration.event.web.dto.EventChangeRequestCreateRequest;
import MUDST_2026_Whatsss.event_registration.event.web.dto.EventChangeRequestResponse;
import MUDST_2026_Whatsss.event_registration.event.web.dto.EventWriteRequest;
import MUDST_2026_Whatsss.event_registration.storage.service.EventImageService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
public class AdminEventService {

    private final EventRepository eventRepository;
    private final EventCategoryRepository categoryRepository;
    private final EventAdminAssignmentRepository assignmentRepository;
    private final EventReviewRepository reviewRepository;
    private final EventChangeRequestRepository changeRequestRepository;
    private final EventChangeRequestItemRepository changeRequestItemRepository;
    private final AuditLogRepository auditRepository;
    private final AuthUserRepository userRepository;
    private final EventMapper mapper;
    private final ObjectMapper objectMapper;

    public AdminEventService(
            EventRepository eventRepository,
            EventCategoryRepository categoryRepository,
            EventAdminAssignmentRepository assignmentRepository,
            EventReviewRepository reviewRepository,
            EventChangeRequestRepository changeRequestRepository,
            EventChangeRequestItemRepository changeRequestItemRepository,
            AuditLogRepository auditRepository,
            AuthUserRepository userRepository,
            EventMapper mapper,
            ObjectMapper objectMapper) {
        this.eventRepository = eventRepository;
        this.categoryRepository = categoryRepository;
        this.assignmentRepository = assignmentRepository;
        this.reviewRepository = reviewRepository;
        this.changeRequestRepository = changeRequestRepository;
        this.changeRequestItemRepository = changeRequestItemRepository;
        this.auditRepository = auditRepository;
        this.userRepository = userRepository;
        this.mapper = mapper;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public Page<AdminEventResponse> list(
            AuthenticatedUser principal, EventStatus status, String query, Pageable pageable) {
        String normalizedQuery = query == null ? "" : query.trim();
        Page<Event> events = principal.hasRole(RoleCodes.SUPER_ADMIN)
                ? eventRepository.findAllFiltered(status, normalizedQuery, pageable)
                : eventRepository.findAssignedToFiltered(
                        principal.userId(), status, normalizedQuery, pageable);
        return events.map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public AdminEventStatsResponse stats(AuthenticatedUser principal) {
        return new AdminEventStatsResponse(
                count(principal, null),
                count(principal, EventStatus.DRAFT),
                count(principal, EventStatus.PENDING_REVIEW),
                count(principal, EventStatus.PUBLISHED),
                count(principal, EventStatus.REJECTED),
                count(principal, EventStatus.CANCELLED));
    }

    @Transactional(readOnly = true)
    public AdminEventResponse get(UUID eventId, AuthenticatedUser principal) {
        Event event = eventRepository.findById(eventId).orElseThrow(EventNotFoundException::new);
        requireScope(event, principal);
        return toResponse(event);
    }

    @Transactional
    public AdminEventResponse create(EventWriteRequest request, AuthenticatedUser principal) {
        AuthUser actor = userRepository.findById(principal.userId())
                .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHENTICATED));
        Event event = Event.builder()
                .slug(uniqueSlug(request.title()))
                .createdBy(actor)
                .status(EventStatus.DRAFT)
                .build();
        apply(event, request, principal, true);
        event = eventRepository.saveAndFlush(event);

        assignmentRepository.save(EventAdminAssignment.builder()
                .event(event)
                .adminUser(actor)
                .assignedBy(actor)
                .assignmentRole(EventAdminAssignment.ROLE_OWNER)
                .build());
        audit(actor, "EVENT_CREATED", event, Map.of("status", event.getStatus().name()));
        return toResponse(event);
    }

    @Transactional
    public AdminEventResponse update(
            UUID eventId, long expectedVersion, EventWriteRequest request,
            AuthenticatedUser principal) {
        Event event = locked(eventId, principal);
        requireVersion(event, expectedVersion);
        if (event.getStatus() != EventStatus.DRAFT && event.getStatus() != EventStatus.REJECTED) {
            throw new ApiException(ErrorCode.INVALID_EVENT_STATE,
                    "Only draft or rejected events can be edited directly.");
        }
        apply(event, request, principal, false);
        event = eventRepository.saveAndFlush(event);
        audit(actor(event, principal), "EVENT_UPDATED", event, Map.of("version", event.getVersion()));
        return toResponse(event);
    }

    @Transactional
    public AdminEventResponse submit(UUID eventId, long expectedVersion, AuthenticatedUser principal) {
        Event event = locked(eventId, principal);
        requireVersion(event, expectedVersion);
        if (event.getStatus() != EventStatus.DRAFT && event.getStatus() != EventStatus.REJECTED) {
            throw new ApiException(ErrorCode.INVALID_EVENT_STATE,
                    "Only draft or rejected events can be submitted.");
        }
        if (reviewRepository.findByEvent_EventIdAndDecision(eventId, EventReview.PENDING).isPresent()) {
            throw new ApiException(ErrorCode.INVALID_EVENT_STATE, "This event is already awaiting review.");
        }

        event.setStatus(EventStatus.PENDING_REVIEW);
        event = eventRepository.saveAndFlush(event);
        AuthUser actor = actor(event, principal);
        reviewRepository.save(EventReview.builder()
                .event(event)
                .eventVersion(event.getVersion())
                .submittedBy(actor)
                .build());
        audit(actor, "EVENT_SUBMITTED", event, Map.of("version", event.getVersion()));
        return toResponse(event);
    }

    @Transactional
    public AdminEventResponse withdraw(UUID eventId, long expectedVersion, AuthenticatedUser principal) {
        Event event = locked(eventId, principal);
        requireVersion(event, expectedVersion);
        if (event.getStatus() != EventStatus.PENDING_REVIEW) {
            throw new ApiException(ErrorCode.INVALID_EVENT_STATE,
                    "Only an event awaiting review can be withdrawn.");
        }
        EventReview review = reviewRepository
                .findByEvent_EventIdAndDecision(eventId, EventReview.PENDING)
                .orElseThrow(() -> new ApiException(ErrorCode.INVALID_EVENT_STATE));
        review.setDecision(EventReview.WITHDRAWN);
        review.setReviewedAt(Instant.now());
        event.setStatus(EventStatus.DRAFT);
        event = eventRepository.saveAndFlush(event);
        audit(actor(event, principal), "EVENT_WITHDRAWN", event, Map.of("version", event.getVersion()));
        return toResponse(event);
    }

    @Transactional
    public AdminEventResponse cancel(
            UUID eventId, CancelEventRequest request, AuthenticatedUser principal) {
        Event event = locked(eventId, principal);
        requireVersion(event, request.version());
        if (event.getStatus() == EventStatus.CANCELLED || event.getStatus() == EventStatus.COMPLETED) {
            throw new ApiException(ErrorCode.INVALID_EVENT_STATE,
                    "A cancelled or completed event cannot be cancelled again.");
        }
        reviewRepository.findByEvent_EventIdAndDecision(eventId, EventReview.PENDING)
                .ifPresent(review -> {
                    review.setDecision(EventReview.WITHDRAWN);
                    review.setReviewedAt(Instant.now());
                });
        event.setStatus(EventStatus.CANCELLED);
        event.setCancelledAt(Instant.now());
        event.setCancellationReason(request.reason().trim());
        event = eventRepository.saveAndFlush(event);
        audit(actor(event, principal), "EVENT_CANCELLED", event,
                Map.of("reason", event.getCancellationReason(), "version", event.getVersion()));
        return toResponse(event);
    }

    @Transactional(readOnly = true)
    public List<EventChangeRequestResponse> changeRequests(
            UUID eventId, AuthenticatedUser principal) {
        Event event = eventRepository.findById(eventId).orElseThrow(EventNotFoundException::new);
        requireScope(event, principal);
        return changeRequestRepository.findByEvent_EventIdOrderByCreatedAtDesc(eventId).stream()
                .map(this::toChangeRequestResponse)
                .toList();
    }

    @Transactional
    public EventChangeRequestResponse requestChanges(
            UUID eventId,
            long expectedVersion,
            EventChangeRequestCreateRequest request,
            AuthenticatedUser principal) {
        Event event = locked(eventId, principal);
        requireVersion(event, expectedVersion);
        if (event.getStatus() != EventStatus.PUBLISHED) {
            throw new ApiException(ErrorCode.INVALID_EVENT_STATE,
                    "Only a published event uses the change-request workflow.");
        }
        if (changeRequestRepository.existsByEvent_EventIdAndStatus(
                eventId, EventChangeRequest.PENDING)) {
            throw new ApiException(ErrorCode.INVALID_EVENT_STATE,
                    "This event already has a pending change request.");
        }

        EventWriteRequest proposedEvent = request.event();
        EventCategory category = categoryRepository.findById(proposedEvent.eventCategoryId())
                .filter(EventCategory::isActive)
                .orElseThrow(() -> new ApiException(ErrorCode.EVENT_CATEGORY_NOT_FOUND));
        String nextImage = blankToNull(proposedEvent.imageObjectKey());
        if (!Objects.equals(event.getImageObjectKey(), nextImage)) {
            requireOwnedImage(nextImage, principal);
        }

        Map<String, Object> current = changeValues(event);
        Map<String, Object> proposed = changeValues(proposedEvent, category);
        List<EventChangeRequestItem> items = new ArrayList<>();

        AuthUser actor = actor(event, principal);
        EventChangeRequest changeRequest = changeRequestRepository.saveAndFlush(
                EventChangeRequest.builder()
                        .event(event)
                        .requestedBy(actor)
                        .reason(request.reason().trim())
                        .build());

        int order = 0;
        for (Map.Entry<String, Object> entry : proposed.entrySet()) {
            JsonNode oldValue = objectMapper.valueToTree(current.get(entry.getKey()));
            JsonNode newValue = objectMapper.valueToTree(entry.getValue());
            if (!oldValue.equals(newValue)) {
                items.add(EventChangeRequestItem.builder()
                        .changeRequest(changeRequest)
                        .fieldName(entry.getKey())
                        .oldValue(oldValue)
                        .newValue(newValue)
                        .displayOrder(order++)
                        .build());
            }
        }
        if (items.isEmpty()) {
            changeRequestRepository.delete(changeRequest);
            throw new ApiException(ErrorCode.INVALID_EVENT_STATE,
                    "No event fields were changed.");
        }
        items = changeRequestItemRepository.saveAll(items);
        audit(actor, "EVENT_CHANGE_REQUESTED", event,
                Map.of("changeRequestId", changeRequest.getEventChangeRequestId(),
                        "changedFields", items.size()));
        return toChangeRequestResponse(changeRequest, items);
    }

    private Event locked(UUID eventId, AuthenticatedUser principal) {
        Event event = eventRepository.findByIdForUpdate(eventId).orElseThrow(EventNotFoundException::new);
        requireScope(event, principal);
        return event;
    }

    private void requireScope(Event event, AuthenticatedUser principal) {
        if (principal.hasRole(RoleCodes.SUPER_ADMIN)) {
            return;
        }
        boolean assigned = assignmentRepository.existsByEvent_EventIdAndAdminUser_UserIdAndStatus(
                event.getEventId(), principal.userId(), EventAdminAssignment.STATUS_ACTIVE);
        if (!assigned) {
            throw new EventNotFoundException();
        }
    }

    private void requireVersion(Event event, long expectedVersion) {
        if (event.getVersion() != expectedVersion) {
            throw new ApiException(ErrorCode.EVENT_VERSION_CONFLICT);
        }
    }

    private AuthUser actor(Event event, AuthenticatedUser principal) {
        if (event.getCreatedBy().getUserId().equals(principal.userId())) {
            return event.getCreatedBy();
        }
        return userRepository.findById(principal.userId())
                .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHENTICATED));
    }

    private void apply(
            Event event, EventWriteRequest request, AuthenticatedUser principal, boolean creating) {
        EventCategory category = categoryRepository.findById(request.eventCategoryId())
                .filter(EventCategory::isActive)
                .orElseThrow(() -> new ApiException(ErrorCode.EVENT_CATEGORY_NOT_FOUND));

        String nextImage = blankToNull(request.imageObjectKey());
        if (creating || !Objects.equals(event.getImageObjectKey(), nextImage)) {
            requireOwnedImage(nextImage, principal);
        }

        event.setTitle(request.title().trim());
        event.setSummary(blankToNull(request.summary()));
        event.setDescription(blankToNull(request.description()));
        event.setCategory(category);
        event.setEventType(request.eventType());
        event.setPrice(request.price());
        event.setCurrency(request.currency().trim().toUpperCase(Locale.ROOT));
        event.setRefundPolicy(blankToNull(request.refundPolicy()));
        event.setLocationType(request.locationType());
        event.setLocationName(blankToNull(request.locationName()));
        event.setAddress(blankToNull(request.address()));
        event.setOnlineUrl(blankToNull(request.onlineUrl()));
        event.setImageObjectKey(nextImage);
        event.setTimezone(request.timezone().trim());
        event.setStartAt(request.startAt());
        event.setEndAt(request.endAt());
        event.setRegistrationStartAt(request.registrationStartAt());
        event.setRegistrationEndAt(request.registrationEndAt());
        event.setCancellationDeadlineAt(request.cancellationDeadlineAt());
        event.setMaximumParticipants(request.maximumParticipants());
        event.setRules(blankToNull(request.rules()));
        event.setContactEmail(blankToNull(request.contactEmail()));
        event.setEligibility(blankToNull(request.eligibility()));
        event.setAllowCancellation(request.allowCancellation());
        event.setShowRemainingSeats(request.showRemainingSeats());
    }

    private void requireOwnedImage(String objectKey, AuthenticatedUser principal) {
        if (objectKey == null) {
            return;
        }
        if (!objectKey.matches(EventImageService.OBJECT_KEY_REGEX)) {
            throw new ApiException(ErrorCode.INVALID_IMAGE_FILE);
        }
        String prefix = "event-images/" + principal.userId() + "/";
        if (!principal.hasRole(RoleCodes.SUPER_ADMIN) && !objectKey.startsWith(prefix)) {
            throw new ApiException(ErrorCode.ACCESS_DENIED);
        }
    }

    private String uniqueSlug(String title) {
        String base = Normalizer.normalize(title, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-+|-+$)", "");
        if (base.isBlank()) {
            base = "event";
        }
        base = base.substring(0, Math.min(base.length(), 145));
        String slug;
        do {
            slug = base + "-" + UUID.randomUUID().toString().substring(0, 8);
        } while (eventRepository.existsBySlug(slug));
        return slug;
    }

    private AdminEventResponse toResponse(Event event) {
        return mapper.toAdminResponse(event,
                eventRepository.countActiveRegistrations(event.getEventId()));
    }

    private EventChangeRequestResponse toChangeRequestResponse(EventChangeRequest request) {
        return toChangeRequestResponse(request,
                changeRequestItemRepository
                        .findByChangeRequest_EventChangeRequestIdOrderByDisplayOrder(
                                request.getEventChangeRequestId()));
    }

    private EventChangeRequestResponse toChangeRequestResponse(
            EventChangeRequest request, List<EventChangeRequestItem> items) {
        return new EventChangeRequestResponse(
                request.getEventChangeRequestId(),
                request.getEvent().getEventId(),
                request.getStatus(),
                request.getReason(),
                request.getReviewComment(),
                request.getReviewedAt(),
                request.getCreatedAt(),
                request.getVersion(),
                items.stream()
                        .map(item -> new EventChangeRequestResponse.Item(
                                item.getFieldName(), item.getOldValue(), item.getNewValue(),
                                item.getDisplayOrder()))
                        .toList());
    }

    private Map<String, Object> changeValues(Event event) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("title", event.getTitle());
        values.put("summary", event.getSummary());
        values.put("description", event.getDescription());
        values.put("event_category_id",
                event.getCategory() == null ? null : event.getCategory().getEventCategoryId());
        values.put("event_type", event.getEventType());
        values.put("price", event.getPrice());
        values.put("currency", event.getCurrency().trim());
        values.put("refund_policy", event.getRefundPolicy());
        values.put("location_type", event.getLocationType());
        values.put("location_name", event.getLocationName());
        values.put("address", event.getAddress());
        values.put("online_url", event.getOnlineUrl());
        values.put("image_object_key", event.getImageObjectKey());
        values.put("timezone", event.getTimezone());
        values.put("start_at", event.getStartAt());
        values.put("end_at", event.getEndAt());
        values.put("registration_start_at", event.getRegistrationStartAt());
        values.put("registration_end_at", event.getRegistrationEndAt());
        values.put("cancellation_deadline_at", event.getCancellationDeadlineAt());
        values.put("maximum_participants", event.getMaximumParticipants());
        values.put("rules", event.getRules());
        values.put("contact_email", event.getContactEmail());
        values.put("eligibility", event.getEligibility());
        values.put("allow_cancellation", event.isAllowCancellation());
        values.put("show_remaining_seats", event.isShowRemainingSeats());
        return values;
    }

    private Map<String, Object> changeValues(EventWriteRequest request, EventCategory category) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("title", request.title().trim());
        values.put("summary", blankToNull(request.summary()));
        values.put("description", blankToNull(request.description()));
        values.put("event_category_id", category.getEventCategoryId());
        values.put("event_type", request.eventType());
        values.put("price", request.price());
        values.put("currency", request.currency().trim().toUpperCase(Locale.ROOT));
        values.put("refund_policy", blankToNull(request.refundPolicy()));
        values.put("location_type", request.locationType());
        values.put("location_name", blankToNull(request.locationName()));
        values.put("address", blankToNull(request.address()));
        values.put("online_url", blankToNull(request.onlineUrl()));
        values.put("image_object_key", blankToNull(request.imageObjectKey()));
        values.put("timezone", request.timezone().trim());
        values.put("start_at", request.startAt());
        values.put("end_at", request.endAt());
        values.put("registration_start_at", request.registrationStartAt());
        values.put("registration_end_at", request.registrationEndAt());
        values.put("cancellation_deadline_at", request.cancellationDeadlineAt());
        values.put("maximum_participants", request.maximumParticipants());
        values.put("rules", blankToNull(request.rules()));
        values.put("contact_email", blankToNull(request.contactEmail()));
        values.put("eligibility", blankToNull(request.eligibility()));
        values.put("allow_cancellation", request.allowCancellation());
        values.put("show_remaining_seats", request.showRemainingSeats());
        return values;
    }

    private long count(AuthenticatedUser principal, EventStatus status) {
        if (principal.hasRole(RoleCodes.SUPER_ADMIN)) {
            return status == null ? eventRepository.count() : eventRepository.countByStatus(status);
        }
        return status == null
                ? eventRepository.countAssigned(principal.userId())
                : eventRepository.countAssignedByStatus(principal.userId(), status);
    }

    private void audit(AuthUser actor, String action, Event event, Map<String, Object> metadata) {
        auditRepository.save(AuditLog.builder()
                .actor(actor)
                .action(action)
                .targetType("EVENT")
                .targetId(event.getEventId())
                .targetLabel(event.getTitle())
                .metadata(metadata)
                .build());
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
