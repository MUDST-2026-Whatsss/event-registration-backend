-- Phase 2: visitors may register without an account. A guest registration has no participant
-- until someone signs up (or signs in) with the same verified email address, at which point it is
-- linked to that account's participant and appears in their history. registered_as_guest stays
-- true after linking so the original channel is kept on record.

ALTER TABLE public.registrations
    ALTER COLUMN participant_id DROP NOT NULL,
    ALTER COLUMN registered_by_user_id DROP NOT NULL,
    ADD COLUMN registered_as_guest boolean DEFAULT false NOT NULL,
    ADD COLUMN guest_linked_at timestamp with time zone;

ALTER TABLE public.registrations
    ADD CONSTRAINT ck_registrations_owner
        CHECK (registered_as_guest OR (participant_id IS NOT NULL AND registered_by_user_id IS NOT NULL)),
    ADD CONSTRAINT ck_registrations_guest_link
        CHECK ((guest_linked_at IS NULL) = (NOT registered_as_guest OR participant_id IS NULL));

-- One active seat per guest email per event; cancelled guest rows do not block a new one.
CREATE UNIQUE INDEX ux_registrations_event_guest_email
    ON public.registrations USING btree (event_id, lower((attendee_email)::text))
    WHERE participant_id IS NULL
      AND (status)::text = ANY (ARRAY['PENDING_PAYMENT', 'CONFIRMED', 'CHECKED_IN']);

-- Looked up on sign-up and sign-in to find guest registrations waiting to be linked.
CREATE INDEX ix_registrations_unlinked_guest_email
    ON public.registrations USING btree (lower((attendee_email)::text))
    WHERE participant_id IS NULL;

COMMENT ON COLUMN public.registrations.participant_id IS
    'Participant who holds the seat; NULL for a guest registration not yet linked to an account.';
COMMENT ON COLUMN public.registrations.registered_by_user_id IS
    'Authenticated account that submitted the registration; NULL when a guest submitted it.';
COMMENT ON COLUMN public.registrations.registered_as_guest IS
    'True when the registration was submitted without signing in; kept after linking.';
COMMENT ON COLUMN public.registrations.guest_linked_at IS
    'When a guest registration was linked to the account that signed up with its email.';
