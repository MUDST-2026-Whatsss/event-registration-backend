package MUDST_2026_Whatsss.event_registration.registration.web;

import MUDST_2026_Whatsss.event_registration.auth.security.AuthenticatedUser;
import MUDST_2026_Whatsss.event_registration.registration.service.RegistrationService;
import MUDST_2026_Whatsss.event_registration.registration.web.dto.CreateRegistrationRequest;
import MUDST_2026_Whatsss.event_registration.registration.web.dto.RegistrationResponse;
import MUDST_2026_Whatsss.event_registration.registration.web.dto.EventInvitationResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * A signed-in participant's own registrations. Guests are rejected by the security chain before
 * reaching here; registration listing lives under {@code /registrations} rather than
 * {@code /events/**} because public GETs on the latter are open to everyone.
 */
@RestController
@RequestMapping("/api/v1")
public class RegistrationController {

    private final RegistrationService service;

    public RegistrationController(RegistrationService service) {
        this.service = service;
    }

    @PostMapping("/events/{eventId}/registrations")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('REGISTRATION_CREATE_SELF')")
    public RegistrationResponse register(
            @PathVariable UUID eventId,
            @Valid @RequestBody CreateRegistrationRequest request,
            @AuthenticationPrincipal AuthenticatedUser user) {
        return service.register(eventId, request, user);
    }

    @GetMapping("/registrations")
    @PreAuthorize("hasAuthority('REGISTRATION_READ_SELF')")
    public List<RegistrationResponse> mine(@AuthenticationPrincipal AuthenticatedUser user) {
        return service.mine(user);
    }

    @GetMapping("/invitations")
    @PreAuthorize("hasAuthority('REGISTRATION_READ_SELF')")
    public List<EventInvitationResponse> invitations(@AuthenticationPrincipal AuthenticatedUser user) {
        return service.invitations(user);
    }

    @GetMapping("/registrations/{registrationId}")
    @PreAuthorize("hasAuthority('REGISTRATION_READ_SELF')")
    public RegistrationResponse get(
            @PathVariable UUID registrationId,
            @AuthenticationPrincipal AuthenticatedUser user) {
        return service.get(registrationId, user);
    }

    @PostMapping("/registrations/{registrationId}/cancel")
    @PreAuthorize("hasAuthority('REGISTRATION_CANCEL_SELF')")
    public RegistrationResponse cancel(
            @PathVariable UUID registrationId,
            @AuthenticationPrincipal AuthenticatedUser user) {
        return service.cancel(registrationId, user);
    }
}
