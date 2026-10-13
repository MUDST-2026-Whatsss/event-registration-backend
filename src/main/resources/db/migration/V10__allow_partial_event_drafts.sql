-- Keep genuine missing draft fields as NULL; never invent dates, prices, or capacity.
ALTER TABLE public.events
    ALTER COLUMN start_at DROP NOT NULL,
    ALTER COLUMN end_at DROP NOT NULL,
    ALTER COLUMN registration_start_at DROP NOT NULL,
    ALTER COLUMN registration_end_at DROP NOT NULL,
    ALTER COLUMN maximum_participants DROP NOT NULL,
    ALTER COLUMN price DROP NOT NULL;

ALTER TABLE public.events DROP CONSTRAINT ck_events_location;
ALTER TABLE public.events ADD CONSTRAINT ck_events_location CHECK (
    status IN ('DRAFT', 'REJECTED', 'CANCELLED')
    OR (location_type = 'ONSITE' AND location_name IS NOT NULL)
    OR (location_type = 'ONLINE' AND online_url IS NOT NULL)
    OR (location_type = 'HYBRID' AND location_name IS NOT NULL AND online_url IS NOT NULL)
);

-- Review/published events must still contain everything required for registrations.
ALTER TABLE public.events ADD CONSTRAINT ck_events_ready_fields CHECK (
    status IN ('DRAFT', 'REJECTED', 'CANCELLED')
    OR (event_category_id IS NOT NULL AND start_at IS NOT NULL AND end_at IS NOT NULL
        AND registration_start_at IS NOT NULL AND registration_end_at IS NOT NULL
        AND maximum_participants IS NOT NULL AND price IS NOT NULL)
);
