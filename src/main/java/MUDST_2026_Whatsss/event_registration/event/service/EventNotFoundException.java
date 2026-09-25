package MUDST_2026_Whatsss.event_registration.event.service;

import MUDST_2026_Whatsss.event_registration.common.error.ApiException;
import MUDST_2026_Whatsss.event_registration.common.error.ErrorCode;

/** Stable not-found error that does not disclose unpublished event details. */
public class EventNotFoundException extends ApiException {

    public EventNotFoundException() {
        super(ErrorCode.EVENT_NOT_FOUND);
    }
}
