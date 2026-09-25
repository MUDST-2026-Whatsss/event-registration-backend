package MUDST_2026_Whatsss.event_registration.event.web;

import MUDST_2026_Whatsss.event_registration.auth.security.AuthenticatedUser;
import MUDST_2026_Whatsss.event_registration.event.domain.EventStatus;
import MUDST_2026_Whatsss.event_registration.event.service.AdminEventService;
import MUDST_2026_Whatsss.event_registration.event.web.dto.AdminEventResponse;
import MUDST_2026_Whatsss.event_registration.event.web.dto.AdminEventStatsResponse;
import MUDST_2026_Whatsss.event_registration.event.web.dto.CancelEventRequest;
import MUDST_2026_Whatsss.event_registration.event.web.dto.EventChangeRequestCreateRequest;
import MUDST_2026_Whatsss.event_registration.event.web.dto.EventChangeRequestResponse;
import MUDST_2026_Whatsss.event_registration.event.web.dto.EventWriteRequest;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;
import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/events")
@PreAuthorize("hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
public class AdminEventController {

    private final AdminEventService service;

    public AdminEventController(AdminEventService service) {
        this.service = service;
    }

    @GetMapping
    public Page<AdminEventResponse> list(
            @RequestParam(required = false) EventStatus status,
            @RequestParam(defaultValue = "") String query,
            @PageableDefault(size = 20, sort = "updatedAt") Pageable pageable,
            @AuthenticationPrincipal AuthenticatedUser user) {
        return service.list(user, status, query, pageable);
    }

    @GetMapping("/stats")
    public AdminEventStatsResponse stats(@AuthenticationPrincipal AuthenticatedUser user) {
        return service.stats(user);
    }

    @GetMapping("/{eventId}")
    public AdminEventResponse get(
            @PathVariable UUID eventId,
            @AuthenticationPrincipal AuthenticatedUser user) {
        return service.get(eventId, user);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('EVENT_CREATE') or hasRole('SUPER_ADMIN')")
    public AdminEventResponse create(
            @Valid @RequestBody EventWriteRequest request,
            @AuthenticationPrincipal AuthenticatedUser user) {
        return service.create(request, user);
    }

    @PatchMapping("/{eventId}")
    @PreAuthorize("hasAuthority('EVENT_UPDATE') or hasRole('SUPER_ADMIN')")
    public AdminEventResponse update(
            @PathVariable UUID eventId,
            @RequestParam long version,
            @Valid @RequestBody EventWriteRequest request,
            @AuthenticationPrincipal AuthenticatedUser user) {
        return service.update(eventId, version, request, user);
    }

    @PostMapping("/{eventId}/submit")
    @PreAuthorize("hasAuthority('EVENT_UPDATE') or hasRole('SUPER_ADMIN')")
    public AdminEventResponse submit(
            @PathVariable UUID eventId,
            @RequestParam long version,
            @AuthenticationPrincipal AuthenticatedUser user) {
        return service.submit(eventId, version, user);
    }

    @PostMapping("/{eventId}/withdraw")
    @PreAuthorize("hasAuthority('EVENT_UPDATE') or hasRole('SUPER_ADMIN')")
    public AdminEventResponse withdraw(
            @PathVariable UUID eventId,
            @RequestParam long version,
            @AuthenticationPrincipal AuthenticatedUser user) {
        return service.withdraw(eventId, version, user);
    }

    @PostMapping("/{eventId}/cancel")
    @PreAuthorize("hasAuthority('EVENT_CANCEL') or hasRole('SUPER_ADMIN')")
    public AdminEventResponse cancel(
            @PathVariable UUID eventId,
            @Valid @RequestBody CancelEventRequest request,
            @AuthenticationPrincipal AuthenticatedUser user) {
        return service.cancel(eventId, request, user);
    }

    @GetMapping("/{eventId}/change-requests")
    public List<EventChangeRequestResponse> changeRequests(
            @PathVariable UUID eventId,
            @AuthenticationPrincipal AuthenticatedUser user) {
        return service.changeRequests(eventId, user);
    }

    @PostMapping("/{eventId}/change-requests")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('EVENT_UPDATE') or hasRole('SUPER_ADMIN')")
    public EventChangeRequestResponse requestChanges(
            @PathVariable UUID eventId,
            @RequestParam long version,
            @Valid @RequestBody EventChangeRequestCreateRequest request,
            @AuthenticationPrincipal AuthenticatedUser user) {
        return service.requestChanges(eventId, version, request, user);
    }
}
