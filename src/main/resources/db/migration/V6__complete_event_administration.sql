-- Complete the database contract required by the current Admin and Super Admin UI.
--
-- This migration is intentionally based on the deployed V5 schema
-- (auth_users/events/registrations use UUID primary keys). It must not be run
-- after the legacy DB_init.sql schema, which uses SERIAL identifiers.

-- ============================================================================
-- 1. Event details and approval lifecycle
-- ============================================================================

ALTER TABLE events
    DROP CONSTRAINT ck_events_status,
    ADD CONSTRAINT ck_events_status CHECK (
        status IN (
            'DRAFT',
            'PENDING_REVIEW',
            'PUBLISHED',
            'REJECTED',
            'CANCELLED',
            'COMPLETED'
        )
    ),
    ADD COLUMN rules TEXT,
    ADD COLUMN contact_email VARCHAR(320),
    ADD COLUMN eligibility TEXT,
    ADD COLUMN allow_cancellation BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN show_remaining_seats BOOLEAN NOT NULL DEFAULT TRUE;

ALTER TABLE events
    ADD CONSTRAINT ck_events_contact_email CHECK (
        contact_email IS NULL
        OR (
            contact_email = btrim(contact_email)
            AND position('@' IN contact_email) > 1
        )
    ),
    ADD CONSTRAINT ck_events_cancellation_policy CHECK (
        allow_cancellation
        OR cancellation_deadline_at IS NULL
    );

COMMENT ON COLUMN events.rules IS
    'Rules shown to participants before registration.';
COMMENT ON COLUMN events.eligibility IS
    'Human-readable eligibility requirements; enforcement belongs in the application service.';
COMMENT ON COLUMN events.allow_cancellation IS
    'Whether participant-initiated cancellation is allowed.';
COMMENT ON COLUMN events.show_remaining_seats IS
    'Controls public display only; capacity is always enforced by the backend.';

CREATE INDEX ix_events_review_queue
    ON events (updated_at DESC, event_id)
    WHERE status = 'PENDING_REVIEW';

-- ============================================================================
-- 2. Event administrator assignments
-- ============================================================================

CREATE TABLE event_admin_assignments (
    event_admin_assignment_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id UUID NOT NULL
        REFERENCES events(event_id) ON DELETE RESTRICT,
    admin_user_id UUID NOT NULL
        REFERENCES auth_users(user_id) ON DELETE RESTRICT,
    assignment_role VARCHAR(30) NOT NULL DEFAULT 'EVENT_ADMIN',
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    assigned_by_user_id UUID
        REFERENCES auth_users(user_id) ON DELETE SET NULL,
    assigned_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    removed_at TIMESTAMPTZ,

    CONSTRAINT ck_event_admin_assignment_role CHECK (
        assignment_role IN ('OWNER', 'EVENT_ADMIN', 'ORGANIZER', 'COORDINATOR')
    ),
    CONSTRAINT ck_event_admin_assignment_status CHECK (
        status IN ('ACTIVE', 'REMOVED')
    ),
    CONSTRAINT ck_event_admin_assignment_removed CHECK (
        (status = 'ACTIVE' AND removed_at IS NULL)
        OR (status = 'REMOVED' AND removed_at IS NOT NULL)
    ),
    CONSTRAINT ck_event_admin_assignment_removed_at CHECK (
        removed_at IS NULL OR removed_at >= assigned_at
    )
);

CREATE UNIQUE INDEX ux_event_admin_assignment_active
    ON event_admin_assignments (event_id, admin_user_id)
    WHERE status = 'ACTIVE';
CREATE INDEX ix_event_admin_assignment_user
    ON event_admin_assignments (admin_user_id, status, assigned_at DESC);
CREATE INDEX ix_event_admin_assignment_event
    ON event_admin_assignments (event_id, status, assigned_at DESC);

-- The creator is the initial owner of an existing event. This makes the
-- migration useful immediately and is safe to run once under Flyway.
INSERT INTO event_admin_assignments (
    event_id,
    admin_user_id,
    assignment_role,
    status,
    assigned_by_user_id
)
SELECT
    e.event_id,
    e.created_by_user_id,
    'OWNER',
    'ACTIVE',
    e.created_by_user_id
FROM events e
ON CONFLICT DO NOTHING;

-- ============================================================================
-- 3. Event submission and review history
-- ============================================================================

CREATE TABLE event_reviews (
    event_review_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id UUID NOT NULL
        REFERENCES events(event_id) ON DELETE RESTRICT,
    event_version BIGINT NOT NULL,
    priority VARCHAR(20) NOT NULL DEFAULT 'STANDARD',
    decision VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    submitted_by_user_id UUID NOT NULL
        REFERENCES auth_users(user_id) ON DELETE RESTRICT,
    reviewed_by_user_id UUID
        REFERENCES auth_users(user_id) ON DELETE SET NULL,
    comment TEXT,
    submitted_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    reviewed_at TIMESTAMPTZ,

    CONSTRAINT ck_event_reviews_version CHECK (event_version >= 0),
    CONSTRAINT ck_event_reviews_priority CHECK (
        priority IN ('STANDARD', 'HIGH')
    ),
    CONSTRAINT ck_event_reviews_decision CHECK (
        decision IN ('PENDING', 'APPROVED', 'REJECTED', 'WITHDRAWN')
    ),
    CONSTRAINT ck_event_reviews_decision_state CHECK (
        (decision = 'PENDING' AND reviewed_at IS NULL AND reviewed_by_user_id IS NULL)
        OR (
            decision IN ('APPROVED', 'REJECTED')
            AND reviewed_at IS NOT NULL
            AND reviewed_by_user_id IS NOT NULL
        )
        OR (decision = 'WITHDRAWN' AND reviewed_at IS NOT NULL)
    ),
    CONSTRAINT ck_event_reviews_reviewed_at CHECK (
        reviewed_at IS NULL OR reviewed_at >= submitted_at
    )
);

CREATE UNIQUE INDEX ux_event_reviews_pending
    ON event_reviews (event_id)
    WHERE decision = 'PENDING';
CREATE INDEX ix_event_reviews_queue
    ON event_reviews (priority DESC, submitted_at, event_review_id)
    WHERE decision = 'PENDING';
CREATE INDEX ix_event_reviews_event_history
    ON event_reviews (event_id, submitted_at DESC);

-- ============================================================================
-- 4. Change requests and field-level comparison
-- ============================================================================

CREATE TABLE event_change_requests (
    event_change_request_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id UUID NOT NULL
        REFERENCES events(event_id) ON DELETE RESTRICT,
    requested_by_user_id UUID NOT NULL
        REFERENCES auth_users(user_id) ON DELETE RESTRICT,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    reason TEXT,
    reviewed_by_user_id UUID
        REFERENCES auth_users(user_id) ON DELETE SET NULL,
    review_comment TEXT,
    reviewed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    version BIGINT NOT NULL DEFAULT 0,

    CONSTRAINT ck_event_change_requests_status CHECK (
        status IN ('PENDING', 'APPROVED', 'REJECTED', 'WITHDRAWN')
    ),
    CONSTRAINT ck_event_change_requests_review_state CHECK (
        (status = 'PENDING' AND reviewed_at IS NULL AND reviewed_by_user_id IS NULL)
        OR (
            status IN ('APPROVED', 'REJECTED')
            AND reviewed_at IS NOT NULL
            AND reviewed_by_user_id IS NOT NULL
        )
        OR (status = 'WITHDRAWN' AND reviewed_at IS NOT NULL)
    ),
    CONSTRAINT ck_event_change_requests_reviewed_at CHECK (
        reviewed_at IS NULL OR reviewed_at >= created_at
    ),
    CONSTRAINT ck_event_change_requests_updated_at CHECK (
        updated_at >= created_at
    ),
    CONSTRAINT ck_event_change_requests_version CHECK (version >= 0)
);

CREATE TABLE event_change_request_items (
    event_change_request_item_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_change_request_id UUID NOT NULL
        REFERENCES event_change_requests(event_change_request_id) ON DELETE CASCADE,
    field_name VARCHAR(100) NOT NULL,
    old_value JSONB,
    new_value JSONB,
    display_order INTEGER NOT NULL DEFAULT 0,

    CONSTRAINT ck_event_change_request_items_field CHECK (
        field_name ~ '^[a-z][a-z0-9_]*$'
    ),
    CONSTRAINT ck_event_change_request_items_changed CHECK (
        old_value IS DISTINCT FROM new_value
    ),
    CONSTRAINT ck_event_change_request_items_order CHECK (display_order >= 0),
    CONSTRAINT ux_event_change_request_items_field UNIQUE (
        event_change_request_id,
        field_name
    )
);

CREATE UNIQUE INDEX ux_event_change_requests_pending
    ON event_change_requests (event_id)
    WHERE status = 'PENDING';
CREATE INDEX ix_event_change_requests_requester
    ON event_change_requests (requested_by_user_id, created_at DESC);
CREATE INDEX ix_event_change_requests_queue
    ON event_change_requests (created_at, event_change_request_id)
    WHERE status = 'PENDING';
CREATE INDEX ix_event_change_request_items_request
    ON event_change_request_items (event_change_request_id, display_order);

-- ============================================================================
-- 5. Registration snapshot and lifecycle history
-- ============================================================================

ALTER TABLE registrations
    ADD COLUMN attendee_name VARCHAR(255),
    ADD COLUMN attendee_email VARCHAR(320),
    ADD COLUMN attendee_phone VARCHAR(30),
    ADD COLUMN consent_accepted_at TIMESTAMPTZ;

UPDATE registrations r
SET
    attendee_name = COALESCE(
        NULLIF(btrim(concat_ws(' ', p.first_name, p.last_name)), ''),
        u.email
    ),
    attendee_email = u.email,
    attendee_phone = p.phone_number
FROM participants p
JOIN auth_users u ON u.user_id = p.user_id
WHERE p.participant_id = r.participant_id;

ALTER TABLE registrations
    ALTER COLUMN attendee_name SET NOT NULL,
    ALTER COLUMN attendee_email SET NOT NULL,
    ADD CONSTRAINT ck_registrations_attendee_name CHECK (
        length(btrim(attendee_name)) > 0
    ),
    ADD CONSTRAINT ck_registrations_attendee_email CHECK (
        attendee_email = btrim(attendee_email)
        AND position('@' IN attendee_email) > 1
    );

COMMENT ON COLUMN registrations.consent_accepted_at IS
    'Consent timestamp captured for new registrations. Nullable for legacy records; the registration service must require it for new writes.';

CREATE TABLE registration_status_history (
    registration_status_history_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    registration_id UUID NOT NULL
        REFERENCES registrations(registration_id) ON DELETE RESTRICT,
    from_status VARCHAR(30),
    to_status VARCHAR(30) NOT NULL,
    changed_by_user_id UUID
        REFERENCES auth_users(user_id) ON DELETE SET NULL,
    reason TEXT,
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    changed_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT ck_registration_status_history_from CHECK (
        from_status IS NULL OR from_status IN (
            'PENDING_PAYMENT', 'CONFIRMED', 'PAYMENT_EXPIRED',
            'CANCELLED_BY_USER', 'CANCELLED_BY_ADMIN', 'CANCELLED_EVENT',
            'CHECKED_IN', 'NO_SHOW', 'REFUND_PENDING', 'REFUNDED'
        )
    ),
    CONSTRAINT ck_registration_status_history_to CHECK (
        to_status IN (
            'PENDING_PAYMENT', 'CONFIRMED', 'PAYMENT_EXPIRED',
            'CANCELLED_BY_USER', 'CANCELLED_BY_ADMIN', 'CANCELLED_EVENT',
            'CHECKED_IN', 'NO_SHOW', 'REFUND_PENDING', 'REFUNDED'
        )
    ),
    CONSTRAINT ck_registration_status_history_change CHECK (
        from_status IS DISTINCT FROM to_status
    ),
    CONSTRAINT ck_registration_status_history_metadata CHECK (
        jsonb_typeof(metadata) = 'object'
    )
);

CREATE INDEX ix_registration_status_history_registration
    ON registration_status_history (registration_id, changed_at DESC);

-- Existing registrations predate status history, so record their current
-- state as an initial entry. New writes must be recorded by the service.
INSERT INTO registration_status_history (
    registration_id,
    from_status,
    to_status,
    changed_by_user_id,
    reason,
    changed_at
)
SELECT
    r.registration_id,
    NULL,
    r.status,
    r.registered_by_user_id,
    'MIGRATION_BASELINE',
    r.registered_at
FROM registrations r;

-- ============================================================================
-- 6. Role metadata required by role-management screens
-- ============================================================================

ALTER TABLE auth_roles
    ADD COLUMN description TEXT,
    ADD COLUMN is_system BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN scope_type VARCHAR(30) NOT NULL DEFAULT 'ASSIGNED_EVENTS';

UPDATE auth_roles
SET
    is_system = role_code IN ('USER', 'ADMIN', 'SUPER_ADMIN'),
    scope_type = CASE role_code
        WHEN 'USER' THEN 'SELF'
        WHEN 'SUPER_ADMIN' THEN 'GLOBAL'
        ELSE 'ASSIGNED_EVENTS'
    END;

ALTER TABLE auth_roles
    ADD CONSTRAINT ck_auth_roles_scope_type CHECK (
        scope_type IN ('SELF', 'ASSIGNED_EVENTS', 'GLOBAL')
    );

-- ============================================================================
-- 7. Domain and administration audit log
-- ============================================================================

CREATE TABLE audit_logs (
    audit_log_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    actor_user_id UUID
        REFERENCES auth_users(user_id) ON DELETE SET NULL,
    action VARCHAR(100) NOT NULL,
    target_type VARCHAR(50) NOT NULL,
    target_id UUID,
    target_label VARCHAR(255),
    outcome VARCHAR(20) NOT NULL DEFAULT 'SUCCESS',
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    ip_address INET,
    user_agent VARCHAR(512),
    request_id UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT ck_audit_logs_action CHECK (
        action ~ '^[A-Z][A-Z0-9_]*$'
    ),
    CONSTRAINT ck_audit_logs_target_type CHECK (
        target_type ~ '^[A-Z][A-Z0-9_]*$'
    ),
    CONSTRAINT ck_audit_logs_outcome CHECK (
        outcome IN ('SUCCESS', 'FAILURE')
    ),
    CONSTRAINT ck_audit_logs_metadata CHECK (
        jsonb_typeof(metadata) = 'object'
    )
);

CREATE INDEX ix_audit_logs_recent
    ON audit_logs (created_at DESC, audit_log_id DESC);
CREATE INDEX ix_audit_logs_actor
    ON audit_logs (actor_user_id, created_at DESC);
CREATE INDEX ix_audit_logs_target
    ON audit_logs (target_type, target_id, created_at DESC);
CREATE INDEX ix_audit_logs_action
    ON audit_logs (action, created_at DESC);
CREATE INDEX ix_audit_logs_request
    ON audit_logs (request_id)
    WHERE request_id IS NOT NULL;

COMMENT ON TABLE audit_logs IS
    'Immutable domain/admin audit trail. Authentication attempts remain in auth_login_attempts.';
