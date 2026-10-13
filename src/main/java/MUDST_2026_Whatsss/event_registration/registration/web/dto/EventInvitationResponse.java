package MUDST_2026_Whatsss.event_registration.registration.web.dto;

import MUDST_2026_Whatsss.event_registration.event.web.dto.EventSummaryResponse;
import java.util.UUID;

public record EventInvitationResponse(UUID invitationId, EventSummaryResponse event) {}
