package MUDST_2026_Whatsss.event_registration.event.service;

import MUDST_2026_Whatsss.event_registration.auth.domain.AuthRole;
import MUDST_2026_Whatsss.event_registration.auth.domain.AuthUser;
import MUDST_2026_Whatsss.event_registration.auth.repository.AuthUserRepository;
import MUDST_2026_Whatsss.event_registration.auth.security.AuthenticatedUser;
import MUDST_2026_Whatsss.event_registration.auth.security.RoleCodes;
import MUDST_2026_Whatsss.event_registration.common.error.ApiException;
import MUDST_2026_Whatsss.event_registration.common.error.ErrorCode;
import MUDST_2026_Whatsss.event_registration.event.domain.AuditLog;
import MUDST_2026_Whatsss.event_registration.event.domain.Event;
import MUDST_2026_Whatsss.event_registration.event.domain.EventAdminAssignment;
import MUDST_2026_Whatsss.event_registration.event.domain.EventChangeRequest;
import MUDST_2026_Whatsss.event_registration.event.domain.EventChangeRequestItem;
import MUDST_2026_Whatsss.event_registration.event.domain.EventReview;
import MUDST_2026_Whatsss.event_registration.event.domain.EventStatus;
import MUDST_2026_Whatsss.event_registration.event.domain.EventType;
import MUDST_2026_Whatsss.event_registration.event.domain.LocationType;
import MUDST_2026_Whatsss.event_registration.event.repository.AuditLogRepository;
import MUDST_2026_Whatsss.event_registration.event.repository.EventAdminAssignmentRepository;
import MUDST_2026_Whatsss.event_registration.event.repository.EventCategoryRepository;
import MUDST_2026_Whatsss.event_registration.event.repository.EventChangeRequestItemRepository;
import MUDST_2026_Whatsss.event_registration.event.repository.EventChangeRequestRepository;
import MUDST_2026_Whatsss.event_registration.event.repository.EventRepository;
import MUDST_2026_Whatsss.event_registration.event.repository.EventReviewRepository;
import MUDST_2026_Whatsss.event_registration.event.web.dto.AdminEventResponse;
import MUDST_2026_Whatsss.event_registration.event.web.dto.ChangeRequestReviewResponse;
import MUDST_2026_Whatsss.event_registration.event.web.dto.EventAdminCandidateResponse;
import MUDST_2026_Whatsss.event_registration.event.web.dto.EventAdminResponse;
import MUDST_2026_Whatsss.event_registration.event.web.dto.EventReviewResponse;
import MUDST_2026_Whatsss.event_registration.event.web.dto.ReplaceEventAdminsRequest;
import MUDST_2026_Whatsss.event_registration.event.web.dto.ReviewDecisionRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class SuperAdminEventService {

    private final EventRepository eventRepository;
    private final EventReviewRepository reviewRepository;
    private final EventChangeRequestRepository changeRequestRepository;
    private final EventChangeRequestItemRepository changeItemRepository;
    private final EventAdminAssignmentRepository assignmentRepository;
    private final EventCategoryRepository categoryRepository;
    private final AuthUserRepository userRepository;
    private final AuditLogRepository auditRepository;
    private final EventMapper mapper;
    private final ObjectMapper objectMapper;

    public SuperAdminEventService(
            EventRepository eventRepository,
            EventReviewRepository reviewRepository,
            EventChangeRequestRepository changeRequestRepository,
            EventChangeRequestItemRepository changeItemRepository,
            EventAdminAssignmentRepository assignmentRepository,
            EventCategoryRepository categoryRepository,
            AuthUserRepository userRepository,
            AuditLogRepository auditRepository,
            EventMapper mapper,
            ObjectMapper objectMapper) {
        this.eventRepository = eventRepository;
        this.reviewRepository = reviewRepository;
        this.changeRequestRepository = changeRequestRepository;
        this.changeItemRepository = changeItemRepository;
        this.assignmentRepository = assignmentRepository;
        this.categoryRepository = categoryRepository;
        this.userRepository = userRepository;
        this.auditRepository = auditRepository;
        this.mapper = mapper;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public Page<EventReviewResponse> reviews(String decision, Pageable pageable) {
        Page<EventReview> reviews = decision == null || decision.isBlank()
                ? reviewRepository.findAll(pageable)
                : reviewRepository.findByDecision(decision.trim().toUpperCase(), pageable);
        return reviews.map(this::toReviewResponse);
    }

    @Transactional(readOnly = true)
    public EventReviewResponse review(UUID reviewId) {
        return toReviewResponse(reviewRepository.findDetailById(reviewId)
                .orElseThrow(() -> new ApiException(ErrorCode.EVENT_REVIEW_NOT_FOUND)));
    }

    @Transactional
    public EventReviewResponse approveReview(
            UUID reviewId, ReviewDecisionRequest request, AuthenticatedUser principal) {
        EventReview review = lockPendingReview(reviewId);
        Event event = review.getEvent();
        requireEventVersion(event, request.version());
        if (review.getEventVersion() != event.getVersion()
                || event.getStatus() != EventStatus.PENDING_REVIEW) {
            throw new ApiException(ErrorCode.EVENT_VERSION_CONFLICT);
        }
        AuthUser actor = actor(principal);
        Instant now = Instant.now();
        review.setDecision(EventReview.APPROVED);
        review.setReviewedBy(actor);
        review.setReviewedAt(now);
        review.setComment(blankToNull(request.comment()));
        event.setStatus(EventStatus.PUBLISHED);
        event.setPublishedAt(now);
        eventRepository.saveAndFlush(event);
        audit(actor, "EVENT_REVIEW_APPROVED", event,
                Map.of("reviewId", reviewId, "submittedVersion", review.getEventVersion()));
        return toReviewResponse(review);
    }

    @Transactional
    public EventReviewResponse rejectReview(
            UUID reviewId, ReviewDecisionRequest request, AuthenticatedUser principal) {
        String comment = requiredComment(request.comment());
        EventReview review = lockPendingReview(reviewId);
        Event event = review.getEvent();
        requireEventVersion(event, request.version());
        if (review.getEventVersion() != event.getVersion()
                || event.getStatus() != EventStatus.PENDING_REVIEW) {
            throw new ApiException(ErrorCode.EVENT_VERSION_CONFLICT);
        }
        AuthUser actor = actor(principal);
        review.setDecision(EventReview.REJECTED);
        review.setReviewedBy(actor);
        review.setReviewedAt(Instant.now());
        review.setComment(comment);
        event.setStatus(EventStatus.REJECTED);
        eventRepository.saveAndFlush(event);
        audit(actor, "EVENT_REVIEW_REJECTED", event,
                Map.of("reviewId", reviewId, "comment", comment));
        return toReviewResponse(review);
    }

    @Transactional(readOnly = true)
    public Page<ChangeRequestReviewResponse> changeRequests(String status, Pageable pageable) {
        Page<EventChangeRequest> requests = status == null || status.isBlank()
                ? changeRequestRepository.findAll(pageable)
                : changeRequestRepository.findByStatus(status.trim().toUpperCase(), pageable);
        return requests.map(this::toChangeRequestResponse);
    }

    @Transactional(readOnly = true)
    public ChangeRequestReviewResponse changeRequest(UUID requestId) {
        return toChangeRequestResponse(changeRequestRepository.findDetailById(requestId)
                .orElseThrow(() -> new ApiException(ErrorCode.CHANGE_REQUEST_NOT_FOUND)));
    }

    @Transactional
    public ChangeRequestReviewResponse approveChangeRequest(
            UUID requestId, ReviewDecisionRequest decision, AuthenticatedUser principal) {
        EventChangeRequest request = lockPendingChangeRequest(requestId, decision.version());
        Event event = request.getEvent();
        if (event.getStatus() != EventStatus.PUBLISHED) {
            throw new ApiException(ErrorCode.INVALID_EVENT_STATE,
                    "Only changes to a published event can be approved.");
        }
        List<EventChangeRequestItem> items = changeItemRepository
                .findByChangeRequest_EventChangeRequestIdOrderByDisplayOrder(requestId);
        ensureCurrentValuesUnchanged(event, items);
        items.forEach(item -> applyChange(event, item));

        AuthUser actor = actor(principal);
        request.setStatus(EventChangeRequest.APPROVED);
        request.setReviewedBy(actor);
        request.setReviewComment(blankToNull(decision.comment()));
        request.setReviewedAt(Instant.now());
        eventRepository.saveAndFlush(event);
        audit(actor, "EVENT_CHANGE_APPROVED", event,
                Map.of("changeRequestId", requestId, "changedFields", items.size()));
        return toChangeRequestResponse(request, items);
    }

    @Transactional
    public ChangeRequestReviewResponse rejectChangeRequest(
            UUID requestId, ReviewDecisionRequest decision, AuthenticatedUser principal) {
        String comment = requiredComment(decision.comment());
        EventChangeRequest request = lockPendingChangeRequest(requestId, decision.version());
        AuthUser actor = actor(principal);
        request.setStatus(EventChangeRequest.REJECTED);
        request.setReviewedBy(actor);
        request.setReviewComment(comment);
        request.setReviewedAt(Instant.now());
        audit(actor, "EVENT_CHANGE_REJECTED", request.getEvent(),
                Map.of("changeRequestId", requestId, "comment", comment));
        return toChangeRequestResponse(request);
    }

    @Transactional(readOnly = true)
    public List<EventAdminCandidateResponse> adminCandidates() {
        return userRepository.findActiveByRoleCode(RoleCodes.ADMIN).stream()
                .map(user -> new EventAdminCandidateResponse(user.getUserId(), user.getEmail()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<EventAdminResponse> eventAdmins(UUID eventId) {
        requireEvent(eventId);
        return assignmentRepository
                .findByEvent_EventIdAndStatusOrderByAssignedAt(
                        eventId, EventAdminAssignment.STATUS_ACTIVE)
                .stream().map(this::toAdminResponse).toList();
    }

    @Transactional
    public List<EventAdminResponse> replaceEventAdmins(
            UUID eventId, ReplaceEventAdminsRequest request, AuthenticatedUser principal) {
        Event event = eventRepository.findByIdForUpdate(eventId)
                .orElseThrow(EventNotFoundException::new);
        AuthUser actor = actor(principal);
        Set<UUID> requestedIds = request.adminUserIds();
        Map<UUID, AuthUser> selectedUsers = requestedIds.stream()
                .map(id -> userRepository.findByIdWithRoles(id)
                        .filter(this::isActiveAdmin)
                        .orElseThrow(() -> new ApiException(ErrorCode.ADMIN_USER_NOT_FOUND)))
                .collect(Collectors.toMap(AuthUser::getUserId, user -> user));

        Instant now = Instant.now();
        List<EventAdminAssignment> active = assignmentRepository.findActiveByEventForUpdate(eventId);
        Set<UUID> protectedOwnerIds = active.stream()
                .filter(a -> EventAdminAssignment.ROLE_OWNER.equals(a.getAssignmentRole()))
                .map(a -> a.getAdminUser().getUserId())
                .collect(Collectors.toSet());
        for (EventAdminAssignment assignment : active) {
            UUID userId = assignment.getAdminUser().getUserId();
            if (!EventAdminAssignment.ROLE_OWNER.equals(assignment.getAssignmentRole())
                    && !requestedIds.contains(userId)) {
                assignment.setStatus(EventAdminAssignment.STATUS_REMOVED);
                assignment.setRemovedAt(now);
                audit(actor, "EVENT_ADMIN_REMOVED", event, Map.of("adminUserId", userId));
            }
        }
        Set<UUID> activeIds = active.stream()
                .filter(a -> EventAdminAssignment.STATUS_ACTIVE.equals(a.getStatus()))
                .map(a -> a.getAdminUser().getUserId())
                .collect(Collectors.toSet());
        selectedUsers.values().stream()
                .filter(user -> !activeIds.contains(user.getUserId()))
                .forEach(user -> {
                    assignmentRepository.save(EventAdminAssignment.builder()
                            .event(event)
                            .adminUser(user)
                            .assignedBy(actor)
                            .assignmentRole(EventAdminAssignment.ROLE_EVENT_ADMIN)
                            .assignedAt(now)
                            .build());
                    audit(actor, "EVENT_ADMIN_ASSIGNED", event,
                            Map.of("adminUserId", user.getUserId()));
                });

        audit(actor, "EVENT_ADMINS_REPLACED", event,
                Map.of("adminUserIds", requestedIds, "ownerUserIds", protectedOwnerIds));
        assignmentRepository.flush();
        return assignmentRepository
                .findByEvent_EventIdAndStatusOrderByAssignedAt(
                        eventId, EventAdminAssignment.STATUS_ACTIVE)
                .stream().map(this::toAdminResponse).toList();
    }

    private EventReview lockPendingReview(UUID reviewId) {
        EventReview review = reviewRepository.findByIdForUpdate(reviewId)
                .orElseThrow(() -> new ApiException(ErrorCode.EVENT_REVIEW_NOT_FOUND));
        if (!EventReview.PENDING.equals(review.getDecision())) {
            throw new ApiException(ErrorCode.INVALID_EVENT_STATE, "This review was already decided.");
        }
        return review;
    }

    private EventChangeRequest lockPendingChangeRequest(UUID requestId, long version) {
        EventChangeRequest request = changeRequestRepository.findByIdForUpdate(requestId)
                .orElseThrow(() -> new ApiException(ErrorCode.CHANGE_REQUEST_NOT_FOUND));
        if (!EventChangeRequest.PENDING.equals(request.getStatus())) {
            throw new ApiException(ErrorCode.INVALID_EVENT_STATE,
                    "This change request was already decided.");
        }
        if (request.getVersion() != version) {
            throw new ApiException(ErrorCode.EVENT_VERSION_CONFLICT);
        }
        return request;
    }

    private void requireEventVersion(Event event, long expectedVersion) {
        if (event.getVersion() != expectedVersion) {
            throw new ApiException(ErrorCode.EVENT_VERSION_CONFLICT);
        }
    }

    private void ensureCurrentValuesUnchanged(Event event, List<EventChangeRequestItem> items) {
        Map<String, Object> current = currentValues(event);
        for (EventChangeRequestItem item : items) {
            JsonNode value = objectMapper.valueToTree(current.get(item.getFieldName()));
            if (!value.equals(item.getOldValue())) {
                throw new ApiException(ErrorCode.EVENT_VERSION_CONFLICT,
                        "The event changed after this request was submitted.");
            }
        }
    }

    private Map<String, Object> currentValues(Event event) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("title", event.getTitle());
        values.put("summary", event.getSummary());
        values.put("description", event.getDescription());
        values.put("event_category_id", event.getCategory() == null ? null
                : event.getCategory().getEventCategoryId());
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

    private void applyChange(Event event, EventChangeRequestItem item) {
        JsonNode value = item.getNewValue();
        switch (item.getFieldName()) {
            case "title" -> event.setTitle(text(value));
            case "summary" -> event.setSummary(nullableText(value));
            case "description" -> event.setDescription(nullableText(value));
            case "event_category_id" -> event.setCategory(categoryRepository
                    .findById(UUID.fromString(text(value)))
                    .orElseThrow(() -> new ApiException(ErrorCode.EVENT_CATEGORY_NOT_FOUND)));
            case "event_type" -> event.setEventType(EventType.valueOf(text(value)));
            case "price" -> event.setPrice(new BigDecimal(text(value)));
            case "currency" -> event.setCurrency(text(value));
            case "refund_policy" -> event.setRefundPolicy(nullableText(value));
            case "location_type" -> event.setLocationType(LocationType.valueOf(text(value)));
            case "location_name" -> event.setLocationName(nullableText(value));
            case "address" -> event.setAddress(nullableText(value));
            case "online_url" -> event.setOnlineUrl(nullableText(value));
            case "image_object_key" -> event.setImageObjectKey(nullableText(value));
            case "timezone" -> event.setTimezone(text(value));
            case "start_at" -> event.setStartAt(Instant.parse(text(value)));
            case "end_at" -> event.setEndAt(Instant.parse(text(value)));
            case "registration_start_at" -> event.setRegistrationStartAt(Instant.parse(text(value)));
            case "registration_end_at" -> event.setRegistrationEndAt(Instant.parse(text(value)));
            case "cancellation_deadline_at" -> event.setCancellationDeadlineAt(
                    value.isNull() ? null : Instant.parse(text(value)));
            case "maximum_participants" -> event.setMaximumParticipants(value.asInt());
            case "rules" -> event.setRules(nullableText(value));
            case "contact_email" -> event.setContactEmail(nullableText(value));
            case "eligibility" -> event.setEligibility(nullableText(value));
            case "allow_cancellation" -> event.setAllowCancellation(value.asBoolean());
            case "show_remaining_seats" -> event.setShowRemainingSeats(value.asBoolean());
            default -> throw new ApiException(ErrorCode.VALIDATION_FAILED,
                    "Unsupported event field: " + item.getFieldName());
        }
    }

    private EventReviewResponse toReviewResponse(EventReview review) {
        return new EventReviewResponse(
                review.getReviewId(), review.getEventVersion(), review.getPriority(),
                review.getDecision(), review.getComment(), review.getSubmittedAt(),
                review.getReviewedAt(), userSummary(review.getSubmittedBy()),
                userSummary(review.getReviewedBy()), eventResponse(review.getEvent()));
    }

    private ChangeRequestReviewResponse toChangeRequestResponse(EventChangeRequest request) {
        return toChangeRequestResponse(request, changeItemRepository
                .findByChangeRequest_EventChangeRequestIdOrderByDisplayOrder(
                        request.getEventChangeRequestId()));
    }

    private ChangeRequestReviewResponse toChangeRequestResponse(
            EventChangeRequest request, List<EventChangeRequestItem> items) {
        return new ChangeRequestReviewResponse(
                request.getEventChangeRequestId(), request.getStatus(), request.getReason(),
                request.getReviewComment(), request.getCreatedAt(), request.getReviewedAt(),
                request.getVersion(), userSummary(request.getRequestedBy()),
                userSummary(request.getReviewedBy()), eventResponse(request.getEvent()),
                items.stream().map(item -> new ChangeRequestReviewResponse.Item(
                        item.getFieldName(), item.getOldValue(), item.getNewValue(),
                        item.getDisplayOrder())).toList());
    }

    private AdminEventResponse eventResponse(Event event) {
        return mapper.toAdminResponse(event,
                eventRepository.countActiveRegistrations(event.getEventId()));
    }

    private EventAdminResponse toAdminResponse(EventAdminAssignment assignment) {
        return new EventAdminResponse(
                assignment.getAdminUser().getUserId(), assignment.getAdminUser().getEmail(),
                assignment.getAssignmentRole(), assignment.getAssignedAt(),
                EventAdminAssignment.ROLE_OWNER.equals(assignment.getAssignmentRole()));
    }

    private EventReviewResponse.UserSummary userSummary(AuthUser user) {
        return user == null ? null : new EventReviewResponse.UserSummary(
                user.getUserId(), user.getEmail());
    }

    private AuthUser actor(AuthenticatedUser principal) {
        return userRepository.findById(principal.userId())
                .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHENTICATED));
    }

    private Event requireEvent(UUID eventId) {
        return eventRepository.findById(eventId).orElseThrow(EventNotFoundException::new);
    }

    private boolean isActiveAdmin(AuthUser user) {
        return user.getStatus().name().equals("ACTIVE") && user.getRoles().stream()
                .map(AuthRole::getRoleCode).anyMatch(RoleCodes.ADMIN::equals);
    }

    private void audit(AuthUser actor, String action, Event event, Map<String, Object> metadata) {
        auditRepository.save(AuditLog.builder()
                .actor(actor).action(action).targetType("EVENT").targetId(event.getEventId())
                .targetLabel(event.getTitle()).metadata(metadata).build());
    }

    private static String requiredComment(String comment) {
        String normalized = blankToNull(comment);
        if (normalized == null) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED,
                    "A rejection comment is required.");
        }
        return normalized;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String text(JsonNode value) {
        return value.isTextual() ? value.asText() : value.toString();
    }

    private static String nullableText(JsonNode value) {
        return value == null || value.isNull() ? null : text(value);
    }
}
