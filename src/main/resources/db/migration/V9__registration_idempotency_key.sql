ALTER TABLE public.registrations
    ADD COLUMN idempotency_key VARCHAR(100);

CREATE UNIQUE INDEX ux_registrations_idempotency_key
    ON public.registrations (idempotency_key)
    WHERE idempotency_key IS NOT NULL;

COMMENT ON COLUMN public.registrations.idempotency_key IS
    'Client-generated key used to safely retry registration creation requests.';
