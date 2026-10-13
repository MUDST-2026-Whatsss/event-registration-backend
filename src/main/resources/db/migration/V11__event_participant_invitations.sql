CREATE TABLE public.event_invitations (
    invitation_id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id uuid NOT NULL REFERENCES public.events(event_id) ON DELETE CASCADE,
    user_id uuid NOT NULL REFERENCES public.auth_users(user_id),
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    accepted_at timestamptz,
    CONSTRAINT uq_event_invitations_event_user UNIQUE (event_id, user_id)
);
CREATE INDEX ix_event_invitations_user_pending ON public.event_invitations(user_id)
    WHERE accepted_at IS NULL;
