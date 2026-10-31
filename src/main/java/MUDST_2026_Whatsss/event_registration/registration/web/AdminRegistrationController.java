package MUDST_2026_Whatsss.event_registration.registration.web;

import MUDST_2026_Whatsss.event_registration.auth.security.AuthenticatedUser;
import MUDST_2026_Whatsss.event_registration.registration.service.RegistrationService;
import MUDST_2026_Whatsss.event_registration.registration.web.dto.AdminRecentRegistrationResponse;
import MUDST_2026_Whatsss.event_registration.registration.web.dto.AdminRegistrationResponse;
import MUDST_2026_Whatsss.event_registration.registration.web.dto.CheckInRequest;
import MUDST_2026_Whatsss.event_registration.registration.web.dto.EventAnalyticsResponse;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Participant list, analytics and check-in for an event, scoped to the event's assigned administrators. */
@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasAuthority('REGISTRATION_VIEW_ALL') or hasRole('SUPER_ADMIN')")
public class AdminRegistrationController {

    private final RegistrationService service;

    public AdminRegistrationController(RegistrationService service) {
        this.service = service;
    }

    @GetMapping("/events/{eventId}/registrations")
    public Page<AdminRegistrationResponse> list(
            @PathVariable UUID eventId,
            @RequestParam(defaultValue = "") String query,
            @PageableDefault(size = 50, sort = "registeredAt", direction = Sort.Direction.ASC)
            Pageable pageable,
            @AuthenticationPrincipal AuthenticatedUser user) {
        return service.listForEvent(eventId, query, pageable, user);
    }

    @GetMapping("/events/{eventId}/analytics")
    public EventAnalyticsResponse analytics(
            @PathVariable UUID eventId,
            @AuthenticationPrincipal AuthenticatedUser user) {
        return service.analytics(eventId, user);
    }

    @PostMapping("/events/{eventId}/check-in")
    @PreAuthorize("hasAuthority('REGISTRATION_CHECK_IN') or hasRole('SUPER_ADMIN')")
    public AdminRegistrationResponse checkIn(
            @PathVariable UUID eventId,
            @Valid @RequestBody CheckInRequest request,
            @AuthenticationPrincipal AuthenticatedUser user) {
        return service.checkIn(eventId, request, user);
    }

    @GetMapping("/registrations/recent")
    public Page<AdminRecentRegistrationResponse> recent(
            @PageableDefault(size = 5, sort = "registeredAt", direction = Sort.Direction.DESC)
            Pageable pageable,
            @AuthenticationPrincipal AuthenticatedUser user) {
        return service.listRecent(pageable, user);
    }
}
