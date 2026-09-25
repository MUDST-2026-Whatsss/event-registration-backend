package MUDST_2026_Whatsss.event_registration.event.web;

import MUDST_2026_Whatsss.event_registration.auth.security.AuthenticatedUser;
import MUDST_2026_Whatsss.event_registration.event.service.SuperAdminEventService;
import MUDST_2026_Whatsss.event_registration.event.web.dto.ChangeRequestReviewResponse;
import MUDST_2026_Whatsss.event_registration.event.web.dto.EventAdminCandidateResponse;
import MUDST_2026_Whatsss.event_registration.event.web.dto.EventAdminResponse;
import MUDST_2026_Whatsss.event_registration.event.web.dto.EventReviewResponse;
import MUDST_2026_Whatsss.event_registration.event.web.dto.ReplaceEventAdminsRequest;
import MUDST_2026_Whatsss.event_registration.event.web.dto.ReviewDecisionRequest;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class SuperAdminEventController {

    private final SuperAdminEventService service;

    public SuperAdminEventController(SuperAdminEventService service) {
        this.service = service;
    }

    @GetMapping("/event-reviews")
    public Page<EventReviewResponse> reviews(
            @RequestParam(required = false) String decision,
            @PageableDefault(size = 20, sort = "submittedAt") Pageable pageable) {
        return service.reviews(decision, pageable);
    }

    @GetMapping("/event-reviews/{reviewId}")
    public EventReviewResponse review(@PathVariable UUID reviewId) {
        return service.review(reviewId);
    }

    @PostMapping("/event-reviews/{reviewId}/approve")
    public EventReviewResponse approveReview(
            @PathVariable UUID reviewId,
            @Valid @RequestBody ReviewDecisionRequest request,
            @AuthenticationPrincipal AuthenticatedUser user) {
        return service.approveReview(reviewId, request, user);
    }

    @PostMapping("/event-reviews/{reviewId}/reject")
    public EventReviewResponse rejectReview(
            @PathVariable UUID reviewId,
            @Valid @RequestBody ReviewDecisionRequest request,
            @AuthenticationPrincipal AuthenticatedUser user) {
        return service.rejectReview(reviewId, request, user);
    }

    @GetMapping("/change-requests")
    public Page<ChangeRequestReviewResponse> changeRequests(
            @RequestParam(required = false) String status,
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
        return service.changeRequests(status, pageable);
    }

    @GetMapping("/change-requests/{requestId}")
    public ChangeRequestReviewResponse changeRequest(@PathVariable UUID requestId) {
        return service.changeRequest(requestId);
    }

    @PostMapping("/change-requests/{requestId}/approve")
    public ChangeRequestReviewResponse approveChangeRequest(
            @PathVariable UUID requestId,
            @Valid @RequestBody ReviewDecisionRequest request,
            @AuthenticationPrincipal AuthenticatedUser user) {
        return service.approveChangeRequest(requestId, request, user);
    }

    @PostMapping("/change-requests/{requestId}/reject")
    public ChangeRequestReviewResponse rejectChangeRequest(
            @PathVariable UUID requestId,
            @Valid @RequestBody ReviewDecisionRequest request,
            @AuthenticationPrincipal AuthenticatedUser user) {
        return service.rejectChangeRequest(requestId, request, user);
    }

    @GetMapping("/event-admins")
    public List<EventAdminCandidateResponse> adminCandidates() {
        return service.adminCandidates();
    }

    @GetMapping("/events/{eventId}/admins")
    public List<EventAdminResponse> eventAdmins(@PathVariable UUID eventId) {
        return service.eventAdmins(eventId);
    }

    @PutMapping("/events/{eventId}/admins")
    public List<EventAdminResponse> replaceEventAdmins(
            @PathVariable UUID eventId,
            @Valid @RequestBody ReplaceEventAdminsRequest request,
            @AuthenticationPrincipal AuthenticatedUser user) {
        return service.replaceEventAdmins(eventId, request, user);
    }
}
