-- Flyway baseline for new databases at schema version 6.
-- Generated from the validated local PostgreSQL schema on 2026-09-12.
-- Existing databases already at V6 ignore this baseline migration.

--
-- PostgreSQL database dump
--


-- Dumped from database version 18.6 (Debian 18.6-1.pgdg13+2)
-- Dumped by pg_dump version 18.6 (Debian 18.6-1.pgdg13+2)

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET transaction_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

SET default_tablespace = '';

SET default_table_access_method = heap;

--
-- Name: audit_logs; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.audit_logs (
    audit_log_id uuid DEFAULT gen_random_uuid() NOT NULL,
    actor_user_id uuid,
    action character varying(100) NOT NULL,
    target_type character varying(50) NOT NULL,
    target_id uuid,
    target_label character varying(255),
    outcome character varying(20) DEFAULT 'SUCCESS'::character varying NOT NULL,
    metadata jsonb DEFAULT '{}'::jsonb NOT NULL,
    ip_address inet,
    user_agent character varying(512),
    request_id uuid,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT ck_audit_logs_action CHECK (((action)::text ~ '^[A-Z][A-Z0-9_]*$'::text)),
    CONSTRAINT ck_audit_logs_metadata CHECK ((jsonb_typeof(metadata) = 'object'::text)),
    CONSTRAINT ck_audit_logs_outcome CHECK (((outcome)::text = ANY ((ARRAY['SUCCESS'::character varying, 'FAILURE'::character varying])::text[]))),
    CONSTRAINT ck_audit_logs_target_type CHECK (((target_type)::text ~ '^[A-Z][A-Z0-9_]*$'::text))
);


--
-- Name: TABLE audit_logs; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.audit_logs IS 'Immutable domain/admin audit trail. Authentication attempts remain in auth_login_attempts.';


--
-- Name: auth_action_tokens; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.auth_action_tokens (
    action_token_id uuid DEFAULT gen_random_uuid() NOT NULL,
    user_id uuid NOT NULL,
    token_type character varying(32) NOT NULL,
    token_hash character varying(64) NOT NULL,
    expires_at timestamp with time zone NOT NULL,
    used_at timestamp with time zone,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT ck_auth_action_tokens_expiry CHECK ((expires_at > created_at)),
    CONSTRAINT ck_auth_action_tokens_hash CHECK (((token_hash)::text ~ '^[0-9a-f]{64}$'::text)),
    CONSTRAINT ck_auth_action_tokens_type CHECK (((token_type)::text = ANY ((ARRAY['EMAIL_VERIFICATION'::character varying, 'PASSWORD_RESET'::character varying])::text[]))),
    CONSTRAINT ck_auth_action_tokens_used_at CHECK (((used_at IS NULL) OR (used_at >= created_at)))
);


--
-- Name: auth_invitations; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.auth_invitations (
    invitation_id uuid DEFAULT gen_random_uuid() NOT NULL,
    invited_email character varying(320) NOT NULL,
    invited_email_normalized character varying(320) GENERATED ALWAYS AS (lower(btrim((invited_email)::text))) STORED,
    role_id uuid NOT NULL,
    token_hash character varying(64) NOT NULL,
    invited_by_user_id uuid NOT NULL,
    accepted_by_user_id uuid,
    status character varying(16) DEFAULT 'PENDING'::character varying NOT NULL,
    expires_at timestamp with time zone NOT NULL,
    accepted_at timestamp with time zone,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT ck_auth_invitations_accepted CHECK (((((status)::text = 'ACCEPTED'::text) AND (accepted_at IS NOT NULL) AND (accepted_by_user_id IS NOT NULL)) OR ((status)::text <> 'ACCEPTED'::text))),
    CONSTRAINT ck_auth_invitations_email CHECK ((((invited_email)::text = btrim((invited_email)::text)) AND (POSITION(('@'::text) IN (invited_email)) > 1))),
    CONSTRAINT ck_auth_invitations_expiry CHECK ((expires_at > created_at)),
    CONSTRAINT ck_auth_invitations_hash CHECK (((token_hash)::text ~ '^[0-9a-f]{64}$'::text)),
    CONSTRAINT ck_auth_invitations_status CHECK (((status)::text = ANY ((ARRAY['PENDING'::character varying, 'ACCEPTED'::character varying, 'EXPIRED'::character varying, 'REVOKED'::character varying])::text[])))
);


--
-- Name: auth_login_attempts; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.auth_login_attempts (
    login_attempt_id uuid DEFAULT gen_random_uuid() NOT NULL,
    user_id uuid,
    identifier_hash character varying(64) NOT NULL,
    outcome character varying(16) NOT NULL,
    failure_reason character varying(100),
    ip_address inet,
    user_agent character varying(500),
    attempted_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT ck_auth_login_attempts_failure_reason CHECK (((((outcome)::text = 'SUCCESS'::text) AND (failure_reason IS NULL)) OR (((outcome)::text = 'FAILURE'::text) AND (failure_reason IS NOT NULL)))),
    CONSTRAINT ck_auth_login_attempts_identifier_hash CHECK (((identifier_hash)::text ~ '^[0-9a-f]{64}$'::text)),
    CONSTRAINT ck_auth_login_attempts_outcome CHECK (((outcome)::text = ANY ((ARRAY['SUCCESS'::character varying, 'FAILURE'::character varying])::text[])))
);


--
-- Name: TABLE auth_login_attempts; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.auth_login_attempts IS 'Append-only authentication security history; account lock counters remain on auth_users.';


--
-- Name: auth_permissions; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.auth_permissions (
    permission_id uuid DEFAULT gen_random_uuid() NOT NULL,
    permission_code character varying(100) NOT NULL,
    description character varying(255),
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT ck_auth_permissions_code CHECK (((permission_code)::text ~ '^[A-Z][A-Z0-9_]*$'::text))
);


--
-- Name: auth_role_permissions; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.auth_role_permissions (
    role_id uuid NOT NULL,
    permission_id uuid NOT NULL,
    granted_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: auth_roles; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.auth_roles (
    role_id uuid DEFAULT gen_random_uuid() NOT NULL,
    role_code character varying(50) NOT NULL,
    role_name character varying(100) NOT NULL,
    status character varying(16) DEFAULT 'ACTIVE'::character varying NOT NULL,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    description text,
    is_system boolean DEFAULT false NOT NULL,
    scope_type character varying(30) DEFAULT 'ASSIGNED_EVENTS'::character varying NOT NULL,
    CONSTRAINT ck_auth_roles_code CHECK (((role_code)::text ~ '^[A-Z][A-Z0-9_]*$'::text)),
    CONSTRAINT ck_auth_roles_scope_type CHECK (((scope_type)::text = ANY ((ARRAY['SELF'::character varying, 'ASSIGNED_EVENTS'::character varying, 'GLOBAL'::character varying])::text[]))),
    CONSTRAINT ck_auth_roles_status CHECK (((status)::text = ANY ((ARRAY['ACTIVE'::character varying, 'INACTIVE'::character varying])::text[]))),
    CONSTRAINT ck_auth_roles_updated_at CHECK ((updated_at >= created_at))
);


--
-- Name: auth_sessions; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.auth_sessions (
    session_id uuid DEFAULT gen_random_uuid() CONSTRAINT refresh_tokens_refresh_token_id_not_null NOT NULL,
    user_id uuid CONSTRAINT refresh_tokens_user_id_not_null NOT NULL,
    token_hash character varying(64) CONSTRAINT refresh_tokens_token_hash_not_null NOT NULL,
    token_family_id uuid DEFAULT gen_random_uuid() CONSTRAINT refresh_tokens_family_id_not_null NOT NULL,
    parent_session_id uuid,
    user_agent character varying(500),
    ip_address inet,
    expires_at timestamp with time zone CONSTRAINT refresh_tokens_expires_at_not_null NOT NULL,
    revoked_at timestamp with time zone,
    revoked_reason character varying(100),
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP CONSTRAINT refresh_tokens_created_at_not_null NOT NULL,
    CONSTRAINT ck_refresh_tokens_expiry CHECK ((expires_at > created_at)),
    CONSTRAINT ck_refresh_tokens_hash CHECK (((token_hash)::text ~ '^[0-9a-f]{64}$'::text)),
    CONSTRAINT ck_refresh_tokens_revoked_at CHECK (((revoked_at IS NULL) OR (revoked_at >= created_at)))
);


--
-- Name: auth_user_roles; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.auth_user_roles (
    user_id uuid NOT NULL,
    role_id uuid NOT NULL,
    assigned_by_user_id uuid,
    assigned_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: auth_users; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.auth_users (
    user_id uuid DEFAULT gen_random_uuid() CONSTRAINT users_user_id_not_null NOT NULL,
    email character varying(320) CONSTRAINT users_email_not_null NOT NULL,
    email_normalized character varying(320) GENERATED ALWAYS AS (lower(btrim((email)::text))) STORED,
    password_hash character varying(255),
    status character varying(32) DEFAULT 'PENDING_VERIFICATION'::character varying CONSTRAINT users_status_not_null NOT NULL,
    email_verified_at timestamp with time zone,
    invited_by_user_id uuid,
    invited_at timestamp with time zone,
    last_login_at timestamp with time zone,
    failed_login_attempts integer DEFAULT 0 CONSTRAINT users_failed_login_attempts_not_null NOT NULL,
    locked_until timestamp with time zone,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP CONSTRAINT users_created_at_not_null NOT NULL,
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP CONSTRAINT users_updated_at_not_null NOT NULL,
    version bigint DEFAULT 0 CONSTRAINT users_version_not_null NOT NULL,
    CONSTRAINT ck_users_email_trimmed CHECK ((((email)::text = btrim((email)::text)) AND (length((email)::text) > 3) AND (POSITION(('@'::text) IN (email)) > 1))),
    CONSTRAINT ck_users_failed_login_attempts CHECK ((failed_login_attempts >= 0)),
    CONSTRAINT ck_users_invitation_time CHECK (((invited_at IS NULL) OR (invited_at >= created_at))),
    CONSTRAINT ck_users_status CHECK (((status)::text = ANY ((ARRAY['PENDING_VERIFICATION'::character varying, 'INVITED'::character varying, 'ACTIVE'::character varying, 'SUSPENDED'::character varying, 'DISABLED'::character varying])::text[]))),
    CONSTRAINT ck_users_updated_at CHECK ((updated_at >= created_at)),
    CONSTRAINT ck_users_version CHECK ((version >= 0))
);


--
-- Name: TABLE auth_users; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.auth_users IS 'Authentication accounts and lock state; personal profile data lives in participants.';


--
-- Name: event_admin_assignments; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.event_admin_assignments (
    event_admin_assignment_id uuid DEFAULT gen_random_uuid() NOT NULL,
    event_id uuid NOT NULL,
    admin_user_id uuid NOT NULL,
    assignment_role character varying(30) DEFAULT 'EVENT_ADMIN'::character varying NOT NULL,
    status character varying(20) DEFAULT 'ACTIVE'::character varying NOT NULL,
    assigned_by_user_id uuid,
    assigned_at timestamp with time zone DEFAULT now() NOT NULL,
    removed_at timestamp with time zone,
    CONSTRAINT ck_event_admin_assignment_removed CHECK (((((status)::text = 'ACTIVE'::text) AND (removed_at IS NULL)) OR (((status)::text = 'REMOVED'::text) AND (removed_at IS NOT NULL)))),
    CONSTRAINT ck_event_admin_assignment_removed_at CHECK (((removed_at IS NULL) OR (removed_at >= assigned_at))),
    CONSTRAINT ck_event_admin_assignment_role CHECK (((assignment_role)::text = ANY ((ARRAY['OWNER'::character varying, 'EVENT_ADMIN'::character varying, 'ORGANIZER'::character varying, 'COORDINATOR'::character varying])::text[]))),
    CONSTRAINT ck_event_admin_assignment_status CHECK (((status)::text = ANY ((ARRAY['ACTIVE'::character varying, 'REMOVED'::character varying])::text[])))
);


--
-- Name: event_categories; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.event_categories (
    event_category_id uuid DEFAULT gen_random_uuid() NOT NULL,
    code character varying(50) NOT NULL,
    name_th character varying(150) NOT NULL,
    name_en character varying(150),
    description text,
    is_active boolean DEFAULT true NOT NULL,
    display_order integer DEFAULT 0 NOT NULL,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT ck_event_categories_code CHECK (((code)::text ~ '^[A-Z0-9_]+$'::text)),
    CONSTRAINT ck_event_categories_display_order CHECK ((display_order >= 0)),
    CONSTRAINT ck_event_categories_name_th CHECK ((length(btrim((name_th)::text)) > 0)),
    CONSTRAINT ck_event_categories_updated_at CHECK ((updated_at >= created_at))
);


--
-- Name: event_change_request_items; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.event_change_request_items (
    event_change_request_item_id uuid DEFAULT gen_random_uuid() CONSTRAINT event_change_request_items_event_change_request_item_i_not_null NOT NULL,
    event_change_request_id uuid NOT NULL,
    field_name character varying(100) NOT NULL,
    old_value jsonb,
    new_value jsonb,
    display_order integer DEFAULT 0 NOT NULL,
    CONSTRAINT ck_event_change_request_items_changed CHECK ((old_value IS DISTINCT FROM new_value)),
    CONSTRAINT ck_event_change_request_items_field CHECK (((field_name)::text ~ '^[a-z][a-z0-9_]*$'::text)),
    CONSTRAINT ck_event_change_request_items_order CHECK ((display_order >= 0))
);


--
-- Name: event_change_requests; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.event_change_requests (
    event_change_request_id uuid DEFAULT gen_random_uuid() NOT NULL,
    event_id uuid NOT NULL,
    requested_by_user_id uuid NOT NULL,
    status character varying(20) DEFAULT 'PENDING'::character varying NOT NULL,
    reason text,
    reviewed_by_user_id uuid,
    review_comment text,
    reviewed_at timestamp with time zone,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    version bigint DEFAULT 0 NOT NULL,
    CONSTRAINT ck_event_change_requests_review_state CHECK (((((status)::text = 'PENDING'::text) AND (reviewed_at IS NULL) AND (reviewed_by_user_id IS NULL)) OR (((status)::text = ANY ((ARRAY['APPROVED'::character varying, 'REJECTED'::character varying])::text[])) AND (reviewed_at IS NOT NULL) AND (reviewed_by_user_id IS NOT NULL)) OR (((status)::text = 'WITHDRAWN'::text) AND (reviewed_at IS NOT NULL)))),
    CONSTRAINT ck_event_change_requests_reviewed_at CHECK (((reviewed_at IS NULL) OR (reviewed_at >= created_at))),
    CONSTRAINT ck_event_change_requests_status CHECK (((status)::text = ANY ((ARRAY['PENDING'::character varying, 'APPROVED'::character varying, 'REJECTED'::character varying, 'WITHDRAWN'::character varying])::text[]))),
    CONSTRAINT ck_event_change_requests_updated_at CHECK ((updated_at >= created_at)),
    CONSTRAINT ck_event_change_requests_version CHECK ((version >= 0))
);


--
-- Name: event_reviews; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.event_reviews (
    event_review_id uuid DEFAULT gen_random_uuid() NOT NULL,
    event_id uuid NOT NULL,
    event_version bigint NOT NULL,
    priority character varying(20) DEFAULT 'STANDARD'::character varying NOT NULL,
    decision character varying(20) DEFAULT 'PENDING'::character varying NOT NULL,
    submitted_by_user_id uuid NOT NULL,
    reviewed_by_user_id uuid,
    comment text,
    submitted_at timestamp with time zone DEFAULT now() NOT NULL,
    reviewed_at timestamp with time zone,
    CONSTRAINT ck_event_reviews_decision CHECK (((decision)::text = ANY ((ARRAY['PENDING'::character varying, 'APPROVED'::character varying, 'REJECTED'::character varying, 'WITHDRAWN'::character varying])::text[]))),
    CONSTRAINT ck_event_reviews_decision_state CHECK (((((decision)::text = 'PENDING'::text) AND (reviewed_at IS NULL) AND (reviewed_by_user_id IS NULL)) OR (((decision)::text = ANY ((ARRAY['APPROVED'::character varying, 'REJECTED'::character varying])::text[])) AND (reviewed_at IS NOT NULL) AND (reviewed_by_user_id IS NOT NULL)) OR (((decision)::text = 'WITHDRAWN'::text) AND (reviewed_at IS NOT NULL)))),
    CONSTRAINT ck_event_reviews_priority CHECK (((priority)::text = ANY ((ARRAY['STANDARD'::character varying, 'HIGH'::character varying])::text[]))),
    CONSTRAINT ck_event_reviews_reviewed_at CHECK (((reviewed_at IS NULL) OR (reviewed_at >= submitted_at))),
    CONSTRAINT ck_event_reviews_version CHECK ((event_version >= 0))
);


--
-- Name: events; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.events (
    event_id uuid DEFAULT gen_random_uuid() NOT NULL,
    slug character varying(160) NOT NULL,
    title character varying(255) NOT NULL,
    summary character varying(500),
    description text,
    event_category_id uuid,
    created_by_user_id uuid NOT NULL,
    status character varying(32) DEFAULT 'DRAFT'::character varying NOT NULL,
    event_type character varying(16) DEFAULT 'FREE'::character varying NOT NULL,
    price numeric(12,2) DEFAULT 0 NOT NULL,
    currency character(3) DEFAULT 'THB'::bpchar NOT NULL,
    refund_policy text,
    location_type character varying(16) DEFAULT 'ONSITE'::character varying NOT NULL,
    location_name character varying(255),
    address text,
    online_url text,
    image_url text,
    timezone character varying(64) DEFAULT 'Asia/Bangkok'::character varying NOT NULL,
    start_at timestamp with time zone NOT NULL,
    end_at timestamp with time zone NOT NULL,
    registration_start_at timestamp with time zone NOT NULL,
    registration_end_at timestamp with time zone NOT NULL,
    cancellation_deadline_at timestamp with time zone,
    maximum_participants integer NOT NULL,
    published_at timestamp with time zone,
    cancelled_at timestamp with time zone,
    cancellation_reason character varying(500),
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    version bigint DEFAULT 0 NOT NULL,
    rules text,
    contact_email character varying(320),
    eligibility text,
    allow_cancellation boolean DEFAULT true NOT NULL,
    show_remaining_seats boolean DEFAULT true NOT NULL,
    CONSTRAINT ck_events_cancellation_deadline CHECK (((cancellation_deadline_at IS NULL) OR (cancellation_deadline_at <= start_at))),
    CONSTRAINT ck_events_cancellation_policy CHECK ((allow_cancellation OR (cancellation_deadline_at IS NULL))),
    CONSTRAINT ck_events_cancelled_at CHECK ((((status)::text <> 'CANCELLED'::text) OR (cancelled_at IS NOT NULL))),
    CONSTRAINT ck_events_capacity CHECK ((maximum_participants > 0)),
    CONSTRAINT ck_events_contact_email CHECK (((contact_email IS NULL) OR (((contact_email)::text = btrim((contact_email)::text)) AND (POSITION(('@'::text) IN (contact_email)) > 1)))),
    CONSTRAINT ck_events_currency CHECK ((currency ~ '^[A-Z]{3}$'::text)),
    CONSTRAINT ck_events_location CHECK (((((location_type)::text = 'ONSITE'::text) AND (location_name IS NOT NULL)) OR (((location_type)::text = 'ONLINE'::text) AND (online_url IS NOT NULL)) OR (((location_type)::text = 'HYBRID'::text) AND (location_name IS NOT NULL) AND (online_url IS NOT NULL)))),
    CONSTRAINT ck_events_location_type CHECK (((location_type)::text = ANY ((ARRAY['ONSITE'::character varying, 'ONLINE'::character varying, 'HYBRID'::character varying])::text[]))),
    CONSTRAINT ck_events_price CHECK (((((event_type)::text = 'FREE'::text) AND (price = (0)::numeric)) OR (((event_type)::text = 'PAID'::text) AND (price > (0)::numeric)))),
    CONSTRAINT ck_events_published_at CHECK ((((status)::text <> 'PUBLISHED'::text) OR (published_at IS NOT NULL))),
    CONSTRAINT ck_events_registration_window CHECK (((registration_end_at > registration_start_at) AND (registration_end_at <= start_at))),
    CONSTRAINT ck_events_schedule CHECK ((end_at > start_at)),
    CONSTRAINT ck_events_slug CHECK (((slug)::text ~ '^[a-z0-9]+(-[a-z0-9]+)*$'::text)),
    CONSTRAINT ck_events_status CHECK (((status)::text = ANY ((ARRAY['DRAFT'::character varying, 'PENDING_REVIEW'::character varying, 'PUBLISHED'::character varying, 'REJECTED'::character varying, 'CANCELLED'::character varying, 'COMPLETED'::character varying])::text[]))),
    CONSTRAINT ck_events_title CHECK ((length(btrim((title)::text)) > 0)),
    CONSTRAINT ck_events_type CHECK (((event_type)::text = ANY ((ARRAY['FREE'::character varying, 'PAID'::character varying])::text[]))),
    CONSTRAINT ck_events_updated_at CHECK ((updated_at >= created_at)),
    CONSTRAINT ck_events_version CHECK ((version >= 0))
);


--
-- Name: COLUMN events.rules; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.events.rules IS 'Rules shown to participants before registration.';


--
-- Name: COLUMN events.eligibility; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.events.eligibility IS 'Human-readable eligibility requirements; enforcement belongs in the application service.';


--
-- Name: COLUMN events.allow_cancellation; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.events.allow_cancellation IS 'Whether participant-initiated cancellation is allowed.';


--
-- Name: COLUMN events.show_remaining_seats; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.events.show_remaining_seats IS 'Controls public display only; capacity is always enforced by the backend.';


--
-- Name: participants; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.participants (
    participant_id uuid DEFAULT gen_random_uuid() NOT NULL,
    user_id uuid NOT NULL,
    first_name character varying(100),
    last_name character varying(100),
    phone_number character varying(32),
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    version bigint DEFAULT 0 NOT NULL,
    CONSTRAINT ck_participants_updated_at CHECK ((updated_at >= created_at)),
    CONSTRAINT ck_participants_version CHECK ((version >= 0))
);


--
-- Name: TABLE participants; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.participants IS 'Event participant profile associated one-to-one with an authentication account in the current product scope.';


--
-- Name: payment_orders; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.payment_orders (
    payment_order_id uuid DEFAULT gen_random_uuid() NOT NULL,
    order_number character varying(40) NOT NULL,
    idempotency_key character varying(100) NOT NULL,
    event_id uuid NOT NULL,
    payer_user_id uuid CONSTRAINT payment_orders_user_id_not_null NOT NULL,
    registration_id uuid NOT NULL,
    seat_reservation_id uuid NOT NULL,
    provider character varying(50) DEFAULT 'NONE'::character varying NOT NULL,
    provider_order_reference character varying(255),
    status character varying(32) DEFAULT 'PENDING'::character varying NOT NULL,
    amount numeric(12,2) NOT NULL,
    currency character(3) NOT NULL,
    checkout_url text,
    expires_at timestamp with time zone NOT NULL,
    paid_at timestamp with time zone,
    failed_at timestamp with time zone,
    expired_at timestamp with time zone,
    cancelled_at timestamp with time zone,
    refunded_at timestamp with time zone,
    failure_code character varying(100),
    failure_message character varying(500),
    metadata jsonb DEFAULT '{}'::jsonb NOT NULL,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    version bigint DEFAULT 0 NOT NULL,
    CONSTRAINT ck_payment_orders_amount CHECK ((amount > (0)::numeric)),
    CONSTRAINT ck_payment_orders_cancelled_at CHECK (((cancelled_at IS NULL) OR (cancelled_at >= created_at))),
    CONSTRAINT ck_payment_orders_currency CHECK ((currency ~ '^[A-Z]{3}$'::text)),
    CONSTRAINT ck_payment_orders_expired_at CHECK (((expired_at IS NULL) OR (expired_at >= created_at))),
    CONSTRAINT ck_payment_orders_expiry CHECK ((expires_at > created_at)),
    CONSTRAINT ck_payment_orders_failed_at CHECK (((failed_at IS NULL) OR (failed_at >= created_at))),
    CONSTRAINT ck_payment_orders_metadata CHECK ((jsonb_typeof(metadata) = 'object'::text)),
    CONSTRAINT ck_payment_orders_number CHECK (((order_number)::text ~ '^[A-Z0-9-]{8,40}$'::text)),
    CONSTRAINT ck_payment_orders_paid_at CHECK (((paid_at IS NULL) OR (paid_at >= created_at))),
    CONSTRAINT ck_payment_orders_provider CHECK ((length(btrim((provider)::text)) > 0)),
    CONSTRAINT ck_payment_orders_refunded_at CHECK (((refunded_at IS NULL) OR (refunded_at >= created_at))),
    CONSTRAINT ck_payment_orders_status CHECK (((status)::text = ANY ((ARRAY['PENDING'::character varying, 'PROCESSING'::character varying, 'PAID'::character varying, 'FAILED'::character varying, 'EXPIRED'::character varying, 'CANCELLED'::character varying, 'REFUND_PENDING'::character varying, 'PARTIALLY_REFUNDED'::character varying, 'REFUNDED'::character varying])::text[]))),
    CONSTRAINT ck_payment_orders_updated_at CHECK ((updated_at >= created_at)),
    CONSTRAINT ck_payment_orders_version CHECK ((version >= 0))
);


--
-- Name: registration_status_history; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.registration_status_history (
    registration_status_history_id uuid DEFAULT gen_random_uuid() CONSTRAINT registration_status_history_registration_status_histor_not_null NOT NULL,
    registration_id uuid NOT NULL,
    from_status character varying(30),
    to_status character varying(30) NOT NULL,
    changed_by_user_id uuid,
    reason text,
    metadata jsonb DEFAULT '{}'::jsonb NOT NULL,
    changed_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT ck_registration_status_history_change CHECK (((from_status)::text IS DISTINCT FROM (to_status)::text)),
    CONSTRAINT ck_registration_status_history_from CHECK (((from_status IS NULL) OR ((from_status)::text = ANY ((ARRAY['PENDING_PAYMENT'::character varying, 'CONFIRMED'::character varying, 'PAYMENT_EXPIRED'::character varying, 'CANCELLED_BY_USER'::character varying, 'CANCELLED_BY_ADMIN'::character varying, 'CANCELLED_EVENT'::character varying, 'CHECKED_IN'::character varying, 'NO_SHOW'::character varying, 'REFUND_PENDING'::character varying, 'REFUNDED'::character varying])::text[])))),
    CONSTRAINT ck_registration_status_history_metadata CHECK ((jsonb_typeof(metadata) = 'object'::text)),
    CONSTRAINT ck_registration_status_history_to CHECK (((to_status)::text = ANY ((ARRAY['PENDING_PAYMENT'::character varying, 'CONFIRMED'::character varying, 'PAYMENT_EXPIRED'::character varying, 'CANCELLED_BY_USER'::character varying, 'CANCELLED_BY_ADMIN'::character varying, 'CANCELLED_EVENT'::character varying, 'CHECKED_IN'::character varying, 'NO_SHOW'::character varying, 'REFUND_PENDING'::character varying, 'REFUNDED'::character varying])::text[])))
);


--
-- Name: registrations; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.registrations (
    registration_id uuid DEFAULT gen_random_uuid() NOT NULL,
    registration_number character varying(32) NOT NULL,
    event_id uuid NOT NULL,
    status character varying(32) NOT NULL,
    qr_token_hash character varying(64),
    registered_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    confirmed_at timestamp with time zone,
    cancelled_at timestamp with time zone,
    cancellation_reason character varying(500),
    checked_in_at timestamp with time zone,
    checked_in_by_user_id uuid,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    version bigint DEFAULT 0 NOT NULL,
    participant_id uuid NOT NULL,
    registered_by_user_id uuid NOT NULL,
    attendee_name character varying(255) NOT NULL,
    attendee_email character varying(320) NOT NULL,
    attendee_phone character varying(30),
    consent_accepted_at timestamp with time zone,
    CONSTRAINT ck_registrations_attendee_email CHECK ((((attendee_email)::text = btrim((attendee_email)::text)) AND (POSITION(('@'::text) IN (attendee_email)) > 1))),
    CONSTRAINT ck_registrations_attendee_name CHECK ((length(btrim((attendee_name)::text)) > 0)),
    CONSTRAINT ck_registrations_cancelled_at CHECK (((cancelled_at IS NULL) OR (cancelled_at >= registered_at))),
    CONSTRAINT ck_registrations_check_in_state CHECK ((((status)::text <> 'CHECKED_IN'::text) OR (checked_in_at IS NOT NULL))),
    CONSTRAINT ck_registrations_checked_in_at CHECK (((checked_in_at IS NULL) OR (checked_in_at >= registered_at))),
    CONSTRAINT ck_registrations_confirmed_at CHECK (((confirmed_at IS NULL) OR (confirmed_at >= registered_at))),
    CONSTRAINT ck_registrations_number CHECK (((registration_number)::text ~ '^[A-Z0-9-]{8,32}$'::text)),
    CONSTRAINT ck_registrations_qr_token_hash CHECK (((qr_token_hash IS NULL) OR ((qr_token_hash)::text ~ '^[0-9a-f]{64}$'::text))),
    CONSTRAINT ck_registrations_status CHECK (((status)::text = ANY ((ARRAY['PENDING_PAYMENT'::character varying, 'CONFIRMED'::character varying, 'PAYMENT_EXPIRED'::character varying, 'CANCELLED_BY_USER'::character varying, 'CANCELLED_BY_ADMIN'::character varying, 'CANCELLED_EVENT'::character varying, 'CHECKED_IN'::character varying, 'NO_SHOW'::character varying, 'REFUND_PENDING'::character varying, 'REFUNDED'::character varying])::text[]))),
    CONSTRAINT ck_registrations_updated_at CHECK ((updated_at >= created_at)),
    CONSTRAINT ck_registrations_version CHECK ((version >= 0))
);


--
-- Name: COLUMN registrations.consent_accepted_at; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.registrations.consent_accepted_at IS 'Consent timestamp captured for new registrations. Nullable for legacy records; the registration service must require it for new writes.';


--
-- Name: seat_reservations; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.seat_reservations (
    seat_reservation_id uuid DEFAULT gen_random_uuid() NOT NULL,
    event_id uuid NOT NULL,
    registration_id uuid,
    reservation_token_hash character varying(64) NOT NULL,
    status character varying(16) DEFAULT 'ACTIVE'::character varying NOT NULL,
    quantity smallint DEFAULT 1 NOT NULL,
    expires_at timestamp with time zone NOT NULL,
    consumed_at timestamp with time zone,
    cancelled_at timestamp with time zone,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    version bigint DEFAULT 0 NOT NULL,
    participant_id uuid NOT NULL,
    reserved_by_user_id uuid NOT NULL,
    CONSTRAINT ck_seat_reservations_cancelled_at CHECK (((cancelled_at IS NULL) OR (cancelled_at >= created_at))),
    CONSTRAINT ck_seat_reservations_consumed_at CHECK (((consumed_at IS NULL) OR (consumed_at >= created_at))),
    CONSTRAINT ck_seat_reservations_expiry CHECK ((expires_at > created_at)),
    CONSTRAINT ck_seat_reservations_quantity CHECK ((quantity = 1)),
    CONSTRAINT ck_seat_reservations_status CHECK (((status)::text = ANY ((ARRAY['ACTIVE'::character varying, 'CONSUMED'::character varying, 'EXPIRED'::character varying, 'CANCELLED'::character varying])::text[]))),
    CONSTRAINT ck_seat_reservations_token_hash CHECK (((reservation_token_hash)::text ~ '^[0-9a-f]{64}$'::text)),
    CONSTRAINT ck_seat_reservations_updated_at CHECK ((updated_at >= created_at)),
    CONSTRAINT ck_seat_reservations_version CHECK ((version >= 0))
);


--
-- Name: audit_logs audit_logs_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.audit_logs
    ADD CONSTRAINT audit_logs_pkey PRIMARY KEY (audit_log_id);


--
-- Name: auth_action_tokens auth_action_tokens_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.auth_action_tokens
    ADD CONSTRAINT auth_action_tokens_pkey PRIMARY KEY (action_token_id);


--
-- Name: auth_invitations auth_invitations_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.auth_invitations
    ADD CONSTRAINT auth_invitations_pkey PRIMARY KEY (invitation_id);


--
-- Name: auth_login_attempts auth_login_attempts_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.auth_login_attempts
    ADD CONSTRAINT auth_login_attempts_pkey PRIMARY KEY (login_attempt_id);


--
-- Name: auth_permissions auth_permissions_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.auth_permissions
    ADD CONSTRAINT auth_permissions_pkey PRIMARY KEY (permission_id);


--
-- Name: auth_role_permissions auth_role_permissions_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.auth_role_permissions
    ADD CONSTRAINT auth_role_permissions_pkey PRIMARY KEY (role_id, permission_id);


--
-- Name: auth_roles auth_roles_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.auth_roles
    ADD CONSTRAINT auth_roles_pkey PRIMARY KEY (role_id);


--
-- Name: auth_user_roles auth_user_roles_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.auth_user_roles
    ADD CONSTRAINT auth_user_roles_pkey PRIMARY KEY (user_id, role_id);


--
-- Name: event_admin_assignments event_admin_assignments_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.event_admin_assignments
    ADD CONSTRAINT event_admin_assignments_pkey PRIMARY KEY (event_admin_assignment_id);


--
-- Name: event_categories event_categories_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.event_categories
    ADD CONSTRAINT event_categories_pkey PRIMARY KEY (event_category_id);


--
-- Name: event_change_request_items event_change_request_items_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.event_change_request_items
    ADD CONSTRAINT event_change_request_items_pkey PRIMARY KEY (event_change_request_item_id);


--
-- Name: event_change_requests event_change_requests_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.event_change_requests
    ADD CONSTRAINT event_change_requests_pkey PRIMARY KEY (event_change_request_id);


--
-- Name: event_reviews event_reviews_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.event_reviews
    ADD CONSTRAINT event_reviews_pkey PRIMARY KEY (event_review_id);


--
-- Name: events events_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.events
    ADD CONSTRAINT events_pkey PRIMARY KEY (event_id);


--
-- Name: participants participants_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.participants
    ADD CONSTRAINT participants_pkey PRIMARY KEY (participant_id);


--
-- Name: payment_orders payment_orders_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.payment_orders
    ADD CONSTRAINT payment_orders_pkey PRIMARY KEY (payment_order_id);


--
-- Name: auth_sessions refresh_tokens_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.auth_sessions
    ADD CONSTRAINT refresh_tokens_pkey PRIMARY KEY (session_id);


--
-- Name: registration_status_history registration_status_history_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.registration_status_history
    ADD CONSTRAINT registration_status_history_pkey PRIMARY KEY (registration_status_history_id);


--
-- Name: registrations registrations_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.registrations
    ADD CONSTRAINT registrations_pkey PRIMARY KEY (registration_id);


--
-- Name: seat_reservations seat_reservations_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.seat_reservations
    ADD CONSTRAINT seat_reservations_pkey PRIMARY KEY (seat_reservation_id);


--
-- Name: seat_reservations seat_reservations_registration_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.seat_reservations
    ADD CONSTRAINT seat_reservations_registration_id_key UNIQUE (registration_id);


--
-- Name: auth_users users_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.auth_users
    ADD CONSTRAINT users_pkey PRIMARY KEY (user_id);


--
-- Name: auth_action_tokens ux_auth_action_tokens_hash; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.auth_action_tokens
    ADD CONSTRAINT ux_auth_action_tokens_hash UNIQUE (token_hash);


--
-- Name: auth_invitations ux_auth_invitations_token_hash; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.auth_invitations
    ADD CONSTRAINT ux_auth_invitations_token_hash UNIQUE (token_hash);


--
-- Name: auth_permissions ux_auth_permissions_code; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.auth_permissions
    ADD CONSTRAINT ux_auth_permissions_code UNIQUE (permission_code);


--
-- Name: auth_roles ux_auth_roles_code; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.auth_roles
    ADD CONSTRAINT ux_auth_roles_code UNIQUE (role_code);


--
-- Name: auth_sessions ux_auth_sessions_token_hash; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.auth_sessions
    ADD CONSTRAINT ux_auth_sessions_token_hash UNIQUE (token_hash);


--
-- Name: event_categories ux_event_categories_code; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.event_categories
    ADD CONSTRAINT ux_event_categories_code UNIQUE (code);


--
-- Name: event_change_request_items ux_event_change_request_items_field; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.event_change_request_items
    ADD CONSTRAINT ux_event_change_request_items_field UNIQUE (event_change_request_id, field_name);


--
-- Name: events ux_events_slug; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.events
    ADD CONSTRAINT ux_events_slug UNIQUE (slug);


--
-- Name: participants ux_participants_user; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.participants
    ADD CONSTRAINT ux_participants_user UNIQUE (user_id);


--
-- Name: payment_orders ux_payment_orders_idempotency_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.payment_orders
    ADD CONSTRAINT ux_payment_orders_idempotency_key UNIQUE (idempotency_key);


--
-- Name: payment_orders ux_payment_orders_number; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.payment_orders
    ADD CONSTRAINT ux_payment_orders_number UNIQUE (order_number);


--
-- Name: registrations ux_registrations_event_participant; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.registrations
    ADD CONSTRAINT ux_registrations_event_participant UNIQUE (event_id, participant_id);


--
-- Name: registrations ux_registrations_number; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.registrations
    ADD CONSTRAINT ux_registrations_number UNIQUE (registration_number);


--
-- Name: registrations ux_registrations_qr_token_hash; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.registrations
    ADD CONSTRAINT ux_registrations_qr_token_hash UNIQUE (qr_token_hash);


--
-- Name: seat_reservations ux_seat_reservations_token_hash; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.seat_reservations
    ADD CONSTRAINT ux_seat_reservations_token_hash UNIQUE (reservation_token_hash);


--
-- Name: ix_audit_logs_action; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_audit_logs_action ON public.audit_logs USING btree (action, created_at DESC);


--
-- Name: ix_audit_logs_actor; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_audit_logs_actor ON public.audit_logs USING btree (actor_user_id, created_at DESC);


--
-- Name: ix_audit_logs_recent; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_audit_logs_recent ON public.audit_logs USING btree (created_at DESC, audit_log_id DESC);


--
-- Name: ix_audit_logs_request; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_audit_logs_request ON public.audit_logs USING btree (request_id) WHERE (request_id IS NOT NULL);


--
-- Name: ix_audit_logs_target; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_audit_logs_target ON public.audit_logs USING btree (target_type, target_id, created_at DESC);


--
-- Name: ix_auth_action_tokens_expiry; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_auth_action_tokens_expiry ON public.auth_action_tokens USING btree (expires_at) WHERE (used_at IS NULL);


--
-- Name: ix_auth_action_tokens_user_type_created; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_auth_action_tokens_user_type_created ON public.auth_action_tokens USING btree (user_id, token_type, created_at DESC);


--
-- Name: ix_auth_invitations_expiry; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_auth_invitations_expiry ON public.auth_invitations USING btree (expires_at) WHERE ((status)::text = 'PENDING'::text);


--
-- Name: ix_auth_login_attempts_identifier_time; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_auth_login_attempts_identifier_time ON public.auth_login_attempts USING btree (identifier_hash, attempted_at DESC);


--
-- Name: ix_auth_login_attempts_ip_time; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_auth_login_attempts_ip_time ON public.auth_login_attempts USING btree (ip_address, attempted_at DESC);


--
-- Name: ix_auth_login_attempts_user_time; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_auth_login_attempts_user_time ON public.auth_login_attempts USING btree (user_id, attempted_at DESC);


--
-- Name: ix_auth_role_permissions_permission; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_auth_role_permissions_permission ON public.auth_role_permissions USING btree (permission_id, role_id);


--
-- Name: ix_auth_sessions_expiry; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_auth_sessions_expiry ON public.auth_sessions USING btree (expires_at);


--
-- Name: ix_auth_sessions_family; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_auth_sessions_family ON public.auth_sessions USING btree (token_family_id, created_at);


--
-- Name: ix_auth_sessions_user_active; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_auth_sessions_user_active ON public.auth_sessions USING btree (user_id, expires_at) WHERE (revoked_at IS NULL);


--
-- Name: ix_auth_user_roles_role; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_auth_user_roles_role ON public.auth_user_roles USING btree (role_id, user_id);


--
-- Name: ix_auth_users_locked_until; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_auth_users_locked_until ON public.auth_users USING btree (locked_until) WHERE (locked_until IS NOT NULL);


--
-- Name: ix_auth_users_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_auth_users_status ON public.auth_users USING btree (status, created_at DESC);


--
-- Name: ix_event_admin_assignment_event; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_event_admin_assignment_event ON public.event_admin_assignments USING btree (event_id, status, assigned_at DESC);


--
-- Name: ix_event_admin_assignment_user; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_event_admin_assignment_user ON public.event_admin_assignments USING btree (admin_user_id, status, assigned_at DESC);


--
-- Name: ix_event_categories_active_order; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_event_categories_active_order ON public.event_categories USING btree (is_active, display_order, name_th);


--
-- Name: ix_event_change_request_items_request; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_event_change_request_items_request ON public.event_change_request_items USING btree (event_change_request_id, display_order);


--
-- Name: ix_event_change_requests_queue; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_event_change_requests_queue ON public.event_change_requests USING btree (created_at, event_change_request_id) WHERE ((status)::text = 'PENDING'::text);


--
-- Name: ix_event_change_requests_requester; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_event_change_requests_requester ON public.event_change_requests USING btree (requested_by_user_id, created_at DESC);


--
-- Name: ix_event_reviews_event_history; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_event_reviews_event_history ON public.event_reviews USING btree (event_id, submitted_at DESC);


--
-- Name: ix_event_reviews_queue; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_event_reviews_queue ON public.event_reviews USING btree (priority DESC, submitted_at, event_review_id) WHERE ((decision)::text = 'PENDING'::text);


--
-- Name: ix_events_category_schedule; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_events_category_schedule ON public.events USING btree (event_category_id, start_at) WHERE ((status)::text = 'PUBLISHED'::text);


--
-- Name: ix_events_creator_updated; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_events_creator_updated ON public.events USING btree (created_by_user_id, updated_at DESC);


--
-- Name: ix_events_public_schedule; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_events_public_schedule ON public.events USING btree (status, start_at, event_id);


--
-- Name: ix_events_registration_window; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_events_registration_window ON public.events USING btree (registration_start_at, registration_end_at) WHERE ((status)::text = 'PUBLISHED'::text);


--
-- Name: ix_events_review_queue; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_events_review_queue ON public.events USING btree (updated_at DESC, event_id) WHERE ((status)::text = 'PENDING_REVIEW'::text);


--
-- Name: ix_participants_name; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_participants_name ON public.participants USING btree (last_name, first_name, participant_id);


--
-- Name: ix_payment_orders_payer_created; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_payment_orders_payer_created ON public.payment_orders USING btree (payer_user_id, created_at DESC);


--
-- Name: ix_payment_orders_registration; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_payment_orders_registration ON public.payment_orders USING btree (registration_id, created_at DESC);


--
-- Name: ix_payment_orders_status_expiry; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_payment_orders_status_expiry ON public.payment_orders USING btree (status, expires_at) WHERE ((status)::text = ANY ((ARRAY['PENDING'::character varying, 'PROCESSING'::character varying])::text[]));


--
-- Name: ix_registration_status_history_registration; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_registration_status_history_registration ON public.registration_status_history USING btree (registration_id, changed_at DESC);


--
-- Name: ix_registrations_check_in; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_registrations_check_in ON public.registrations USING btree (event_id, checked_in_at) WHERE ((status)::text = 'CHECKED_IN'::text);


--
-- Name: ix_registrations_event_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_registrations_event_status ON public.registrations USING btree (event_id, status, registered_at);


--
-- Name: ix_registrations_participant_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_registrations_participant_status ON public.registrations USING btree (participant_id, status, registered_at DESC);


--
-- Name: ix_registrations_registered_by; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_registrations_registered_by ON public.registrations USING btree (registered_by_user_id, registered_at DESC);


--
-- Name: ix_seat_reservations_active_expiry; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_seat_reservations_active_expiry ON public.seat_reservations USING btree (expires_at, seat_reservation_id) WHERE ((status)::text = 'ACTIVE'::text);


--
-- Name: ix_seat_reservations_event_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_seat_reservations_event_status ON public.seat_reservations USING btree (event_id, status);


--
-- Name: ix_seat_reservations_reserved_by; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_seat_reservations_reserved_by ON public.seat_reservations USING btree (reserved_by_user_id, created_at DESC);


--
-- Name: ux_auth_invitations_pending_email_role; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX ux_auth_invitations_pending_email_role ON public.auth_invitations USING btree (invited_email_normalized, role_id) WHERE ((status)::text = 'PENDING'::text);


--
-- Name: ux_auth_users_email_normalized; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX ux_auth_users_email_normalized ON public.auth_users USING btree (email_normalized);


--
-- Name: ux_event_admin_assignment_active; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX ux_event_admin_assignment_active ON public.event_admin_assignments USING btree (event_id, admin_user_id) WHERE ((status)::text = 'ACTIVE'::text);


--
-- Name: ux_event_change_requests_pending; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX ux_event_change_requests_pending ON public.event_change_requests USING btree (event_id) WHERE ((status)::text = 'PENDING'::text);


--
-- Name: ux_event_reviews_pending; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX ux_event_reviews_pending ON public.event_reviews USING btree (event_id) WHERE ((decision)::text = 'PENDING'::text);


--
-- Name: ux_payment_orders_provider_reference; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX ux_payment_orders_provider_reference ON public.payment_orders USING btree (provider, provider_order_reference) WHERE (provider_order_reference IS NOT NULL);


--
-- Name: ux_seat_reservations_event_participant_active; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX ux_seat_reservations_event_participant_active ON public.seat_reservations USING btree (event_id, participant_id) WHERE ((status)::text = 'ACTIVE'::text);


--
-- Name: audit_logs audit_logs_actor_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.audit_logs
    ADD CONSTRAINT audit_logs_actor_user_id_fkey FOREIGN KEY (actor_user_id) REFERENCES public.auth_users(user_id) ON DELETE SET NULL;


--
-- Name: auth_action_tokens auth_action_tokens_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.auth_action_tokens
    ADD CONSTRAINT auth_action_tokens_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.auth_users(user_id) ON DELETE CASCADE;


--
-- Name: auth_invitations auth_invitations_accepted_by_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.auth_invitations
    ADD CONSTRAINT auth_invitations_accepted_by_user_id_fkey FOREIGN KEY (accepted_by_user_id) REFERENCES public.auth_users(user_id) ON DELETE SET NULL;


--
-- Name: auth_invitations auth_invitations_invited_by_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.auth_invitations
    ADD CONSTRAINT auth_invitations_invited_by_user_id_fkey FOREIGN KEY (invited_by_user_id) REFERENCES public.auth_users(user_id) ON DELETE RESTRICT;


--
-- Name: auth_invitations auth_invitations_role_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.auth_invitations
    ADD CONSTRAINT auth_invitations_role_id_fkey FOREIGN KEY (role_id) REFERENCES public.auth_roles(role_id) ON DELETE RESTRICT;


--
-- Name: auth_login_attempts auth_login_attempts_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.auth_login_attempts
    ADD CONSTRAINT auth_login_attempts_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.auth_users(user_id) ON DELETE SET NULL;


--
-- Name: auth_role_permissions auth_role_permissions_permission_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.auth_role_permissions
    ADD CONSTRAINT auth_role_permissions_permission_id_fkey FOREIGN KEY (permission_id) REFERENCES public.auth_permissions(permission_id) ON DELETE CASCADE;


--
-- Name: auth_role_permissions auth_role_permissions_role_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.auth_role_permissions
    ADD CONSTRAINT auth_role_permissions_role_id_fkey FOREIGN KEY (role_id) REFERENCES public.auth_roles(role_id) ON DELETE CASCADE;


--
-- Name: auth_user_roles auth_user_roles_assigned_by_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.auth_user_roles
    ADD CONSTRAINT auth_user_roles_assigned_by_user_id_fkey FOREIGN KEY (assigned_by_user_id) REFERENCES public.auth_users(user_id) ON DELETE SET NULL;


--
-- Name: auth_user_roles auth_user_roles_role_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.auth_user_roles
    ADD CONSTRAINT auth_user_roles_role_id_fkey FOREIGN KEY (role_id) REFERENCES public.auth_roles(role_id) ON DELETE RESTRICT;


--
-- Name: auth_user_roles auth_user_roles_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.auth_user_roles
    ADD CONSTRAINT auth_user_roles_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.auth_users(user_id) ON DELETE CASCADE;


--
-- Name: event_admin_assignments event_admin_assignments_admin_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.event_admin_assignments
    ADD CONSTRAINT event_admin_assignments_admin_user_id_fkey FOREIGN KEY (admin_user_id) REFERENCES public.auth_users(user_id) ON DELETE RESTRICT;


--
-- Name: event_admin_assignments event_admin_assignments_assigned_by_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.event_admin_assignments
    ADD CONSTRAINT event_admin_assignments_assigned_by_user_id_fkey FOREIGN KEY (assigned_by_user_id) REFERENCES public.auth_users(user_id) ON DELETE SET NULL;


--
-- Name: event_admin_assignments event_admin_assignments_event_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.event_admin_assignments
    ADD CONSTRAINT event_admin_assignments_event_id_fkey FOREIGN KEY (event_id) REFERENCES public.events(event_id) ON DELETE RESTRICT;


--
-- Name: event_change_request_items event_change_request_items_event_change_request_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.event_change_request_items
    ADD CONSTRAINT event_change_request_items_event_change_request_id_fkey FOREIGN KEY (event_change_request_id) REFERENCES public.event_change_requests(event_change_request_id) ON DELETE CASCADE;


--
-- Name: event_change_requests event_change_requests_event_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.event_change_requests
    ADD CONSTRAINT event_change_requests_event_id_fkey FOREIGN KEY (event_id) REFERENCES public.events(event_id) ON DELETE RESTRICT;


--
-- Name: event_change_requests event_change_requests_requested_by_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.event_change_requests
    ADD CONSTRAINT event_change_requests_requested_by_user_id_fkey FOREIGN KEY (requested_by_user_id) REFERENCES public.auth_users(user_id) ON DELETE RESTRICT;


--
-- Name: event_change_requests event_change_requests_reviewed_by_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.event_change_requests
    ADD CONSTRAINT event_change_requests_reviewed_by_user_id_fkey FOREIGN KEY (reviewed_by_user_id) REFERENCES public.auth_users(user_id) ON DELETE SET NULL;


--
-- Name: event_reviews event_reviews_event_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.event_reviews
    ADD CONSTRAINT event_reviews_event_id_fkey FOREIGN KEY (event_id) REFERENCES public.events(event_id) ON DELETE RESTRICT;


--
-- Name: event_reviews event_reviews_reviewed_by_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.event_reviews
    ADD CONSTRAINT event_reviews_reviewed_by_user_id_fkey FOREIGN KEY (reviewed_by_user_id) REFERENCES public.auth_users(user_id) ON DELETE SET NULL;


--
-- Name: event_reviews event_reviews_submitted_by_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.event_reviews
    ADD CONSTRAINT event_reviews_submitted_by_user_id_fkey FOREIGN KEY (submitted_by_user_id) REFERENCES public.auth_users(user_id) ON DELETE RESTRICT;


--
-- Name: events events_created_by_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.events
    ADD CONSTRAINT events_created_by_user_id_fkey FOREIGN KEY (created_by_user_id) REFERENCES public.auth_users(user_id) ON DELETE RESTRICT;


--
-- Name: events events_event_category_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.events
    ADD CONSTRAINT events_event_category_id_fkey FOREIGN KEY (event_category_id) REFERENCES public.event_categories(event_category_id) ON DELETE RESTRICT;


--
-- Name: registrations fk_registrations_participant; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.registrations
    ADD CONSTRAINT fk_registrations_participant FOREIGN KEY (participant_id) REFERENCES public.participants(participant_id) ON DELETE RESTRICT;


--
-- Name: registrations fk_registrations_registered_by; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.registrations
    ADD CONSTRAINT fk_registrations_registered_by FOREIGN KEY (registered_by_user_id) REFERENCES public.auth_users(user_id) ON DELETE RESTRICT;


--
-- Name: seat_reservations fk_seat_reservations_participant; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.seat_reservations
    ADD CONSTRAINT fk_seat_reservations_participant FOREIGN KEY (participant_id) REFERENCES public.participants(participant_id) ON DELETE RESTRICT;


--
-- Name: seat_reservations fk_seat_reservations_reserved_by; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.seat_reservations
    ADD CONSTRAINT fk_seat_reservations_reserved_by FOREIGN KEY (reserved_by_user_id) REFERENCES public.auth_users(user_id) ON DELETE RESTRICT;


--
-- Name: participants participants_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.participants
    ADD CONSTRAINT participants_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.auth_users(user_id) ON DELETE RESTRICT;


--
-- Name: payment_orders payment_orders_event_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.payment_orders
    ADD CONSTRAINT payment_orders_event_id_fkey FOREIGN KEY (event_id) REFERENCES public.events(event_id) ON DELETE RESTRICT;


--
-- Name: payment_orders payment_orders_registration_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.payment_orders
    ADD CONSTRAINT payment_orders_registration_id_fkey FOREIGN KEY (registration_id) REFERENCES public.registrations(registration_id) ON DELETE RESTRICT;


--
-- Name: payment_orders payment_orders_seat_reservation_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.payment_orders
    ADD CONSTRAINT payment_orders_seat_reservation_id_fkey FOREIGN KEY (seat_reservation_id) REFERENCES public.seat_reservations(seat_reservation_id) ON DELETE RESTRICT;


--
-- Name: payment_orders payment_orders_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.payment_orders
    ADD CONSTRAINT payment_orders_user_id_fkey FOREIGN KEY (payer_user_id) REFERENCES public.auth_users(user_id) ON DELETE RESTRICT;


--
-- Name: auth_sessions refresh_tokens_parent_token_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.auth_sessions
    ADD CONSTRAINT refresh_tokens_parent_token_id_fkey FOREIGN KEY (parent_session_id) REFERENCES public.auth_sessions(session_id) ON DELETE SET NULL;


--
-- Name: auth_sessions refresh_tokens_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.auth_sessions
    ADD CONSTRAINT refresh_tokens_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.auth_users(user_id) ON DELETE CASCADE;


--
-- Name: registration_status_history registration_status_history_changed_by_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.registration_status_history
    ADD CONSTRAINT registration_status_history_changed_by_user_id_fkey FOREIGN KEY (changed_by_user_id) REFERENCES public.auth_users(user_id) ON DELETE SET NULL;


--
-- Name: registration_status_history registration_status_history_registration_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.registration_status_history
    ADD CONSTRAINT registration_status_history_registration_id_fkey FOREIGN KEY (registration_id) REFERENCES public.registrations(registration_id) ON DELETE RESTRICT;


--
-- Name: registrations registrations_checked_in_by_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.registrations
    ADD CONSTRAINT registrations_checked_in_by_user_id_fkey FOREIGN KEY (checked_in_by_user_id) REFERENCES public.auth_users(user_id) ON DELETE SET NULL;


--
-- Name: registrations registrations_event_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.registrations
    ADD CONSTRAINT registrations_event_id_fkey FOREIGN KEY (event_id) REFERENCES public.events(event_id) ON DELETE RESTRICT;


--
-- Name: seat_reservations seat_reservations_event_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.seat_reservations
    ADD CONSTRAINT seat_reservations_event_id_fkey FOREIGN KEY (event_id) REFERENCES public.events(event_id) ON DELETE RESTRICT;


--
-- Name: seat_reservations seat_reservations_registration_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.seat_reservations
    ADD CONSTRAINT seat_reservations_registration_id_fkey FOREIGN KEY (registration_id) REFERENCES public.registrations(registration_id) ON DELETE RESTRICT;


--
-- Name: auth_users users_invited_by_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.auth_users
    ADD CONSTRAINT users_invited_by_user_id_fkey FOREIGN KEY (invited_by_user_id) REFERENCES public.auth_users(user_id) ON DELETE SET NULL;


--
-- PostgreSQL database dump complete
--
-- Required reference data for a usable empty installation.

--
-- PostgreSQL database dump
--


-- Dumped from database version 18.6 (Debian 18.6-1.pgdg13+2)
-- Dumped by pg_dump version 18.6 (Debian 18.6-1.pgdg13+2)

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET transaction_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

--
-- Data for Name: auth_permissions; Type: TABLE DATA; Schema: public; Owner: -
--

INSERT INTO public.auth_permissions (permission_id, permission_code, description, created_at) VALUES ('99a9c09d-6b45-419a-8c9e-228853b2dc9a', 'PROFILE_UPDATE_SELF', 'Update own participant profile', '2026-08-24 16:23:24.370026+00');
INSERT INTO public.auth_permissions (permission_id, permission_code, description, created_at) VALUES ('a74d77e2-d281-4076-a6c3-2b2a02ba4aab', 'REGISTRATION_CREATE_SELF', 'Create own event registration', '2026-08-24 16:23:24.370026+00');
INSERT INTO public.auth_permissions (permission_id, permission_code, description, created_at) VALUES ('667a50df-a3c8-4181-b5b6-7fa8bd4a01c0', 'REGISTRATION_READ_SELF', 'Read own event registrations', '2026-08-24 16:23:24.370026+00');
INSERT INTO public.auth_permissions (permission_id, permission_code, description, created_at) VALUES ('a5b90ea2-5699-417f-843e-316183046da8', 'REGISTRATION_CANCEL_SELF', 'Cancel own event registration', '2026-08-24 16:23:24.370026+00');
INSERT INTO public.auth_permissions (permission_id, permission_code, description, created_at) VALUES ('fd29cebe-0e62-4736-95c9-9d898a0d25bb', 'EVENT_CREATE', 'Create events', '2026-08-24 16:23:24.370026+00');
INSERT INTO public.auth_permissions (permission_id, permission_code, description, created_at) VALUES ('6d71b2a4-98ea-4fc3-a75b-27a4b5ab9e18', 'EVENT_UPDATE', 'Update events', '2026-08-24 16:23:24.370026+00');
INSERT INTO public.auth_permissions (permission_id, permission_code, description, created_at) VALUES ('f7057911-cf76-4894-bcac-c5372ee6c3b2', 'EVENT_PUBLISH', 'Publish events', '2026-08-24 16:23:24.370026+00');
INSERT INTO public.auth_permissions (permission_id, permission_code, description, created_at) VALUES ('11786a44-259e-4dc3-be11-c0d6a2cb9d05', 'EVENT_CANCEL', 'Cancel events', '2026-08-24 16:23:24.370026+00');
INSERT INTO public.auth_permissions (permission_id, permission_code, description, created_at) VALUES ('aca72dcf-400b-4ff1-8672-4d32760159d7', 'REGISTRATION_VIEW_ALL', 'View registrations for managed events', '2026-08-24 16:23:24.370026+00');
INSERT INTO public.auth_permissions (permission_id, permission_code, description, created_at) VALUES ('f262dcc0-1177-4869-b6f9-3e148a249295', 'REGISTRATION_CHECK_IN', 'Check in participants', '2026-08-24 16:23:24.370026+00');
INSERT INTO public.auth_permissions (permission_id, permission_code, description, created_at) VALUES ('f176b9f7-ec7b-44ac-b4f5-dd285b9f8806', 'REGISTRATION_EXPORT', 'Export registration data', '2026-08-24 16:23:24.370026+00');
INSERT INTO public.auth_permissions (permission_id, permission_code, description, created_at) VALUES ('2e4847ee-16a4-4187-ae26-6221d826eaa2', 'USER_VIEW', 'View user accounts', '2026-08-24 16:23:24.370026+00');
INSERT INTO public.auth_permissions (permission_id, permission_code, description, created_at) VALUES ('e84b1f15-89ef-4735-bf03-3bdbd9148db1', 'USER_SUSPEND', 'Suspend or reactivate user accounts', '2026-08-24 16:23:24.370026+00');
INSERT INTO public.auth_permissions (permission_id, permission_code, description, created_at) VALUES ('0c166f27-12fe-4648-ba97-dbf4f8adb52f', 'ROLE_ASSIGN', 'Assign and revoke roles', '2026-08-24 16:23:24.370026+00');
INSERT INTO public.auth_permissions (permission_id, permission_code, description, created_at) VALUES ('1d005a41-fc94-4a64-9179-14afbcf198b2', 'AUDIT_VIEW', 'View security and administration audit data', '2026-08-24 16:23:24.370026+00');


--
-- Data for Name: auth_roles; Type: TABLE DATA; Schema: public; Owner: -
--

INSERT INTO public.auth_roles (role_id, role_code, role_name, status, created_at, updated_at, description, is_system, scope_type) VALUES ('b3fdd633-1c32-448b-8d30-1dcdfffaba0b', 'USER', 'User', 'ACTIVE', '2026-08-24 16:23:24.370026+00', '2026-08-24 16:23:24.370026+00', NULL, true, 'SELF');
INSERT INTO public.auth_roles (role_id, role_code, role_name, status, created_at, updated_at, description, is_system, scope_type) VALUES ('c156fd0a-af61-4173-ac03-cdb672915d38', 'ADMIN', 'Administrator', 'ACTIVE', '2026-08-24 16:23:24.370026+00', '2026-08-24 16:23:24.370026+00', NULL, true, 'ASSIGNED_EVENTS');
INSERT INTO public.auth_roles (role_id, role_code, role_name, status, created_at, updated_at, description, is_system, scope_type) VALUES ('cd2f4610-25ff-481b-a859-615e5b32d62a', 'SUPER_ADMIN', 'Super Administrator', 'ACTIVE', '2026-08-24 16:23:24.370026+00', '2026-08-24 16:23:24.370026+00', NULL, true, 'GLOBAL');


--
-- Data for Name: auth_role_permissions; Type: TABLE DATA; Schema: public; Owner: -
--

INSERT INTO public.auth_role_permissions (role_id, permission_id, granted_at) VALUES ('b3fdd633-1c32-448b-8d30-1dcdfffaba0b', '99a9c09d-6b45-419a-8c9e-228853b2dc9a', '2026-08-24 16:23:24.370026+00');
INSERT INTO public.auth_role_permissions (role_id, permission_id, granted_at) VALUES ('b3fdd633-1c32-448b-8d30-1dcdfffaba0b', 'a74d77e2-d281-4076-a6c3-2b2a02ba4aab', '2026-08-24 16:23:24.370026+00');
INSERT INTO public.auth_role_permissions (role_id, permission_id, granted_at) VALUES ('b3fdd633-1c32-448b-8d30-1dcdfffaba0b', '667a50df-a3c8-4181-b5b6-7fa8bd4a01c0', '2026-08-24 16:23:24.370026+00');
INSERT INTO public.auth_role_permissions (role_id, permission_id, granted_at) VALUES ('b3fdd633-1c32-448b-8d30-1dcdfffaba0b', 'a5b90ea2-5699-417f-843e-316183046da8', '2026-08-24 16:23:24.370026+00');
INSERT INTO public.auth_role_permissions (role_id, permission_id, granted_at) VALUES ('c156fd0a-af61-4173-ac03-cdb672915d38', 'fd29cebe-0e62-4736-95c9-9d898a0d25bb', '2026-08-24 16:23:24.370026+00');
INSERT INTO public.auth_role_permissions (role_id, permission_id, granted_at) VALUES ('c156fd0a-af61-4173-ac03-cdb672915d38', '6d71b2a4-98ea-4fc3-a75b-27a4b5ab9e18', '2026-08-24 16:23:24.370026+00');
INSERT INTO public.auth_role_permissions (role_id, permission_id, granted_at) VALUES ('c156fd0a-af61-4173-ac03-cdb672915d38', 'f7057911-cf76-4894-bcac-c5372ee6c3b2', '2026-08-24 16:23:24.370026+00');
INSERT INTO public.auth_role_permissions (role_id, permission_id, granted_at) VALUES ('c156fd0a-af61-4173-ac03-cdb672915d38', '11786a44-259e-4dc3-be11-c0d6a2cb9d05', '2026-08-24 16:23:24.370026+00');
INSERT INTO public.auth_role_permissions (role_id, permission_id, granted_at) VALUES ('c156fd0a-af61-4173-ac03-cdb672915d38', 'aca72dcf-400b-4ff1-8672-4d32760159d7', '2026-08-24 16:23:24.370026+00');
INSERT INTO public.auth_role_permissions (role_id, permission_id, granted_at) VALUES ('c156fd0a-af61-4173-ac03-cdb672915d38', 'f262dcc0-1177-4869-b6f9-3e148a249295', '2026-08-24 16:23:24.370026+00');
INSERT INTO public.auth_role_permissions (role_id, permission_id, granted_at) VALUES ('c156fd0a-af61-4173-ac03-cdb672915d38', 'f176b9f7-ec7b-44ac-b4f5-dd285b9f8806', '2026-08-24 16:23:24.370026+00');
INSERT INTO public.auth_role_permissions (role_id, permission_id, granted_at) VALUES ('cd2f4610-25ff-481b-a859-615e5b32d62a', '2e4847ee-16a4-4187-ae26-6221d826eaa2', '2026-08-24 16:23:24.370026+00');
INSERT INTO public.auth_role_permissions (role_id, permission_id, granted_at) VALUES ('cd2f4610-25ff-481b-a859-615e5b32d62a', 'e84b1f15-89ef-4735-bf03-3bdbd9148db1', '2026-08-24 16:23:24.370026+00');
INSERT INTO public.auth_role_permissions (role_id, permission_id, granted_at) VALUES ('cd2f4610-25ff-481b-a859-615e5b32d62a', '0c166f27-12fe-4648-ba97-dbf4f8adb52f', '2026-08-24 16:23:24.370026+00');
INSERT INTO public.auth_role_permissions (role_id, permission_id, granted_at) VALUES ('cd2f4610-25ff-481b-a859-615e5b32d62a', '1d005a41-fc94-4a64-9179-14afbcf198b2', '2026-08-24 16:23:24.370026+00');


--
-- Data for Name: event_categories; Type: TABLE DATA; Schema: public; Owner: -
--

INSERT INTO public.event_categories (event_category_id, code, name_th, name_en, description, is_active, display_order, created_at, updated_at) VALUES ('10000000-0000-0000-0000-000000000001', 'TECHNOLOGY', 'เทคโนโลยี', 'Technology', NULL, true, 10, '2026-08-24 17:15:19.865518+00', '2026-08-24 17:15:19.865518+00');
INSERT INTO public.event_categories (event_category_id, code, name_th, name_en, description, is_active, display_order, created_at, updated_at) VALUES ('10000000-0000-0000-0000-000000000002', 'DESIGN', 'การออกแบบ', 'Design', NULL, true, 20, '2026-08-24 17:15:19.865518+00', '2026-08-24 17:15:19.865518+00');
INSERT INTO public.event_categories (event_category_id, code, name_th, name_en, description, is_active, display_order, created_at, updated_at) VALUES ('10000000-0000-0000-0000-000000000003', 'COMMUNITY', 'กิจกรรมชุมชน', 'Community', NULL, true, 30, '2026-08-24 17:15:19.865518+00', '2026-08-24 17:15:19.865518+00');


--
-- PostgreSQL database dump complete
--
