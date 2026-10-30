package MUDST_2026_Whatsss.event_registration.registration.web;

import MUDST_2026_Whatsss.event_registration.registration.service.RegistrationService;
import MUDST_2026_Whatsss.event_registration.registration.web.dto.GuestRegistrationRequest;
import MUDST_2026_Whatsss.event_registration.registration.web.dto.GuestTicketLookupRequest;
import MUDST_2026_Whatsss.event_registration.registration.web.dto.RegistrationResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Registration without an account. Both endpoints are opened in {@code SecurityConfig}; CSRF still
 * applies. The ticket lookup is a POST so the email never appears in a URL or access log.
 */
@RestController
@RequestMapping("/api/v1")
public class GuestRegistrationController {

    private final RegistrationService service;

    public GuestRegistrationController(RegistrationService service) {
        this.service = service;
    }

    @PostMapping("/events/{eventId}/guest-registrations")
    @ResponseStatus(HttpStatus.CREATED)
    public RegistrationResponse register(
            @PathVariable UUID eventId, @Valid @RequestBody GuestRegistrationRequest request) {
        return service.registerGuest(eventId, request);
    }

    @PostMapping("/guest-registrations/lookup")
    public RegistrationResponse lookup(@Valid @RequestBody GuestTicketLookupRequest request) {
        return service.guestTicket(request);
    }
}
