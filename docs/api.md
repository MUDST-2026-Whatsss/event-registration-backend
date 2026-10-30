# API contract

Base path: `/api/v1`

## Current endpoints

| Method | Path | Access | Status |
| --- | --- | --- | --- |
| GET | `/api/health` | Public | Active legacy health probe |
| GET | `/actuator/health` | Public | Active operational health probe |
| GET | `/api/v1/auth/csrf` | Public | Active |
| POST | `/api/v1/auth/register` | Public, rate-limited | Active |
| POST | `/api/v1/auth/forgot-password` | Public, CSRF-protected | Active; sends reset email when SMTP is configured |
| POST | `/api/v1/auth/reset-password` | Public, CSRF-protected | Active; consumes one-use reset token |
| POST | `/api/v1/auth/login` | Public, rate-limited | Active |
| POST | `/api/v1/auth/refresh` | Refresh cookie | Active |
| POST | `/api/v1/auth/select-role` | Authenticated | Active; issues an access token scoped to one assigned role |
| POST | `/api/v1/auth/logout` | Public/idempotent | Active |
| GET | `/api/v1/auth/me` | Authenticated | Active |
| PATCH | `/api/v1/auth/me` | Authenticated | Active |
| POST | `/api/v1/auth/change-password` | Authenticated | Active |
| GET | `/api/v1/event-categories` | Public | Active |
| GET | `/api/v1/events` | Public | Active; all published events, soonest first, with seat availability (clients split upcoming/past by date) |
| GET | `/api/v1/events/{slug}` | Public | Active; published event detail with seat availability |
| POST | `/api/v1/events/{eventId}/registrations` | `REGISTRATION_CREATE_SELF` | Active; free events and classroom paid simulation, capacity enforced under a row lock |
| POST | `/api/v1/registrations/{registrationId}/mock-payment/confirm` | Registration owner | Active only when mock payments are enabled; no real payment |
| POST | `/api/v1/events/{eventId}/guest-registrations` | Public | Active; free events only, one active seat per guest email |
| POST | `/api/v1/guest-registrations/lookup` | Public | Active; guest ticket by registration number and email |
| GET | `/api/v1/invitations` | Authenticated | Active; caller's published event invitations |
| GET | `/api/v1/registrations` | `REGISTRATION_READ_SELF` | Active; caller's registrations, newest first |
| GET | `/api/v1/registrations/{registrationId}` | `REGISTRATION_READ_SELF` | Active; caller's own registration only |
| POST | `/api/v1/registrations/{registrationId}/cancel` | `REGISTRATION_CANCEL_SELF` | Active; within the event's cancellation policy |
| GET | `/api/v1/admin/events` | Assigned ADMIN or SUPER_ADMIN | Active, paginated |
| GET | `/api/v1/admin/registrations/recent` | Assigned ADMIN or SUPER_ADMIN | Active; recent registration activity |
| GET | `/api/v1/admin/events/invitees/lookup?email=` | ADMIN or SUPER_ADMIN | Active; look up an existing participant |
| GET | `/api/v1/admin/events/stats` | Assigned ADMIN or SUPER_ADMIN | Active |
| POST | `/api/v1/admin/events` | `EVENT_CREATE` or SUPER_ADMIN | Active |
| GET | `/api/v1/admin/events/{eventId}` | Assigned ADMIN or SUPER_ADMIN | Active |
| PATCH | `/api/v1/admin/events/{eventId}?version=` | `EVENT_UPDATE`, assigned scope | Active for DRAFT/REJECTED |
| POST | `/api/v1/admin/events/{eventId}/submit?version=` | `EVENT_UPDATE`, assigned scope | Active |
| POST | `/api/v1/admin/events/{eventId}/withdraw?version=` | `EVENT_UPDATE`, assigned scope | Active |
| POST | `/api/v1/admin/events/{eventId}/cancel` | `EVENT_CANCEL`, assigned scope | Active; also cancels every active registration |
| DELETE | `/api/v1/admin/events/{eventId}?version=` | `EVENT_UPDATE`, assigned scope | Active for DRAFT/REJECTED; published events are cancelled instead |
| GET | `/api/v1/admin/events/{eventId}/registrations` | `REGISTRATION_VIEW_ALL`, assigned scope, or SUPER_ADMIN | Active; paginated participant list, `query` searches name/email/number |
| POST | `/api/v1/admin/events/{eventId}/check-in` | `REGISTRATION_CHECK_IN`, assigned scope, or SUPER_ADMIN | Active; checks in a confirmed registration by its registration number |
| GET | `/api/v1/admin/events/{eventId}/analytics` | `REGISTRATION_VIEW_ALL`, assigned scope, or SUPER_ADMIN | Active; signups, drop-offs and show rate for one event |
| GET | `/api/v1/admin/events/{eventId}/change-requests` | Assigned ADMIN or SUPER_ADMIN | Active |
| POST | `/api/v1/admin/events/{eventId}/change-requests?version=` | `EVENT_UPDATE`, published event | Active |
| POST | `/api/v1/event-images` | ADMIN, SUPER_ADMIN | Active when storage is enabled |
| DELETE | `/api/v1/event-images/{ownerId}/{fileName}` | Owner ADMIN or SUPER_ADMIN | Active when storage is enabled |
| GET | `/api/v1/media/event-images/{ownerId}/{fileName}` | Public | Active when storage is enabled |
| GET | `/api/v1/admin/event-reviews` | SUPER_ADMIN | Active; paginated queue/history |
| GET | `/api/v1/admin/event-reviews/{reviewId}` | SUPER_ADMIN | Active |
| POST | `/api/v1/admin/event-reviews/{reviewId}/approve` | SUPER_ADMIN | Active; row lock and version check |
| POST | `/api/v1/admin/event-reviews/{reviewId}/reject` | SUPER_ADMIN | Active; rejection comment required |
| GET | `/api/v1/admin/change-requests` | SUPER_ADMIN | Active; paginated queue/history |
| GET | `/api/v1/admin/change-requests/{requestId}` | SUPER_ADMIN | Active; field-level diff |
| POST | `/api/v1/admin/change-requests/{requestId}/approve` | SUPER_ADMIN | Active; applies stored diff atomically |
| POST | `/api/v1/admin/change-requests/{requestId}/reject` | SUPER_ADMIN | Active; rejection comment required |
| GET | `/api/v1/admin/event-admins` | SUPER_ADMIN | Active; assignment candidates |
| GET | `/api/v1/admin/events/{eventId}/admins` | SUPER_ADMIN | Active |
| PUT | `/api/v1/admin/events/{eventId}/admins` | SUPER_ADMIN | Active; preserves event owner |
| GET | `/api/v1/admin/users` | SUPER_ADMIN | Active; paginated search/status/role filters |
| GET | `/api/v1/admin/users/stats` | SUPER_ADMIN | Active |
| GET | `/api/v1/admin/stats` | SUPER_ADMIN | Active; platform-wide event and user totals |
| GET | `/api/v1/admin/users/{userId}` | SUPER_ADMIN | Active |
| PATCH | `/api/v1/admin/users/{userId}/status` | SUPER_ADMIN | Active; disabling revokes sessions |
| PUT | `/api/v1/admin/users/{userId}/roles` | SUPER_ADMIN | Active; replaces assigned role set and revokes existing sessions |
| GET | `/api/v1/admin/roles` | SUPER_ADMIN | Active; includes permissions and usage count |
| POST | `/api/v1/admin/roles` | SUPER_ADMIN | Active; creates custom role |
| PATCH | `/api/v1/admin/roles/{roleId}` | SUPER_ADMIN | Active; revokes sessions using the role |
| DELETE | `/api/v1/admin/roles/{roleId}` | SUPER_ADMIN | Active; protected system-role rules apply |
| PUT | `/api/v1/admin/roles/{roleId}/permissions` | SUPER_ADMIN | Active; revokes sessions using the role |
| GET | `/api/v1/admin/permissions` | SUPER_ADMIN | Active |
| GET | `/api/v1/admin/audit-logs` | SUPER_ADMIN | Active; immutable paginated history |

Multi-role login returns all assigned role codes but no active permissions until the client calls
`POST /auth/select-role`. Access tokens contain only the selected role and its permissions. The
client includes the selected role when refreshing so a rotated access token keeps the same scope.

The legacy `/api/events` controller has been removed.

## Registrations

This endpoint requires a signed-in account holding `REGISTRATION_CREATE_SELF` (the USER role);
guests receive HTTP 401 here and use the guest endpoint below instead. Only the consent flag is
required; blank attendee fields fall back to the caller's profile and account email.

```json
POST /api/v1/events/{eventId}/registrations
{ "consentAccepted": true, "attendeeName": "Optional", "attendeeEmail": "optional@example.com", "attendeePhone": "Optional" }
```

| Error code | HTTP | When |
| --- | --- | --- |
| `EVENT_NOT_FOUND` | 404 | Event is missing or not published |
| `REGISTRATION_CLOSED` | 409 | Outside `registrationStartAt` to `registrationEndAt` |
| `PAID_REGISTRATION_UNAVAILABLE` | 409 | Mock paid registration is disabled in this environment |
| `ALREADY_REGISTERED` | 409 | Caller already holds an active registration for the event |
| `EVENT_FULL` | 409 | Active registrations have reached `maximumParticipants` |
| `REGISTRATION_NOT_CANCELLABLE` | 409 | Cancellation disabled, deadline passed, or event started |
| `REGISTRATION_NOT_FOUND` | 404 | Unknown ID or another participant's registration |

Capacity counts `PENDING_PAYMENT`, `CONFIRMED`, and `CHECKED_IN`. Public event responses include
`maximumParticipants`, `remainingSeats` (null when the event hides the count), and `full`.
Registering again after a cancellation reactivates the same row and registration number. Every
status change is appended to `registration_status_history`.

Paid registration is a classroom simulation. With `MOCK_PAYMENTS_ENABLED=true`, it creates a
`PENDING_PAYMENT` record. The owner calls the mock confirmation endpoint to persist `CONFIRMED`;
repeated confirmation returns the same registration. No bank transaction or provider webhook is used.

### Guest registration

Visitors may register for free events without an account. Both endpoints are public but still need
the CSRF header.

```json
POST /api/v1/events/{eventId}/guest-registrations
{ "attendeeName": "Required", "attendeeEmail": "required@example.com", "attendeePhone": "Optional", "consentAccepted": true }

POST /api/v1/guest-registrations/lookup
{ "registrationNumber": "REG-XXXXXXXXXX", "email": "required@example.com" }
```

| Error code | HTTP | When |
| --- | --- | --- |
| `GUEST_PAID_REGISTRATION_UNAVAILABLE` | 409 | The event is paid; paying needs a signed-in owner |
| `GUEST_ALREADY_REGISTERED` | 409 | This email (case-insensitive) already holds an active guest seat for the event |
| `REGISTRATION_NOT_FOUND` | 404 | Lookup number and email do not match the same guest registration |

The guest row is stored with `registered_as_guest = true` and no participant or submitting account.
Capacity, registration windows and check-in work the same as for members. Guests cannot cancel
online. Responses include `registeredAsGuest`, also shown on the admin participant list.

When an account with a **verified** email signs up or signs in, every unlinked guest registration
with that email is linked to its participant (`guest_linked_at` is set) and appears in
`GET /registrations`, where the owner can cancel it as usual. Unverified accounts receive nothing,
so signing up with someone else's address does not reveal their ticket. If the account already
holds a registration for the same event, the guest row stays unlinked.

### Check-in

The participant's QR code carries their registration number. Staff send that number, read from the
QR code or typed in by hand, to the check-in endpoint. Case and surrounding spaces are ignored.

```json
POST /api/v1/admin/events/{eventId}/check-in
{ "registrationNumber": "REG-7KQ2M9XA4P" }
```

A successful check-in returns the participant list row with `status` `CHECKED_IN` and
`checkedInAt` set, records the staff member in `checked_in_by_user_id`, and appends a status history
row. A checked-in registration still counts toward capacity.

| Error code | HTTP | When |
| --- | --- | --- |
| `EVENT_NOT_FOUND` | 404 | Event is missing or the caller is not assigned to it |
| `INVALID_EVENT_STATE` | 409 | Event is not published |
| `REGISTRATION_NOT_FOUND` | 404 | Unknown number or a number from another event |
| `ALREADY_CHECKED_IN` | 409 | The registration was already checked in |
| `REGISTRATION_NOT_CHECKABLE` | 409 | The registration is not `CONFIRMED` or `NO_SHOW` (pending payment or cancelled) |

### Event analytics

```json
GET /api/v1/admin/events/{eventId}/analytics
{
  "signups": 120, "memberSignups": 95, "guestSignups": 25,
  "dropOffs": 14, "dropOffRate": 0.117, "cancelledByAdmin": 2,
  "confirmed": 0, "checkedIn": 80, "noShows": 24, "showRate": 0.769,
  "eventEnded": true,
  "dailySignups": [{ "date": "2026-10-20", "count": 12 }]
}
```

- `signups` counts every registration ever made for the event, members and guests.
- `dropOffs` = `CANCELLED_BY_USER` + `PAYMENT_EXPIRED`. Administrator cancellations are
  `cancelledByAdmin`; registrations cancelled with the event are in neither.
- `showRate` = `checkedIn` / (`checkedIn` + `noShows`), and is `null` until `endAt` has passed.
  Once the event has ended, `noShows` also counts registrations still `CONFIRMED`; `confirmed` is the
  raw status count. Rates are `null` when there is nothing to divide by.
- `dailySignups` groups by the registration day in the event's own timezone, oldest first.
- A scheduled job marks registrations still `CONFIRMED` as `NO_SHOW` once `NO_SHOW_GRACE_PERIOD`
  (default 6 hours) has passed since `endAt`, checking every `NO_SHOW_SWEEP_INTERVAL` (default 5
  minutes). A `NO_SHOW` can still be checked in, for late arrivals and offline check-ins synced later.

An administrator not assigned to the event gets `EVENT_NOT_FOUND` (404).

## Event image storage

Upload a cover as `multipart/form-data` using the `file` field. Accepted formats are JPEG, PNG, and
WebP. The default maximum is 2 MB and 6000 x 6000 pixels. The server detects the format from the
file bytes rather than trusting its extension or browser-provided Content-Type.

```json
{
  "objectKey": "event-images/{ownerUserId}/{imageId}.webp",
  "imageUrl": "/api/v1/media/event-images/{ownerUserId}/{imageId}.webp",
  "contentType": "image/webp",
  "sizeBytes": 245761,
  "width": 1600,
  "height": 900
}
```

The Admin create/update and change-request APIs accept `imageObjectKey`, not an arbitrary external
URL. Responses expose the resolved `imageUrl`. Event images are immutable because their keys
contain a generated UUID, so public reads use a one-year immutable cache header.

## Cookies and CSRF

The frontend first calls `GET /api/v1/auth/csrf`. For POST, PUT, PATCH, and DELETE requests it sends
the returned token in `X-XSRF-TOKEN`; cookies are sent with `credentials: include`.

Password recovery uses two public endpoints, both protected by CSRF:

- `POST /api/v1/auth/forgot-password` with `{ "email": "user@example.com" }` always returns the
  same success message so callers cannot discover registered addresses. For an existing local
  account, the API creates a hashed, single-use token and emails a link to the frontend.
- `POST /api/v1/auth/reset-password` with
  `{ "token": "...", "newPassword": "NewPassword123" }` consumes the token, changes the password,
  clears login lockout state, and revokes every existing refresh session for that account.

Reset links expire according to `PASSWORD_RESET_TTL` (30 minutes by default). The database stores
only a SHA-256 digest of each random token, never the usable value from the email.

Access and refresh tokens never appear in JSON response bodies.

An invalid or missing CSRF token returns HTTP 403 with `CSRF_TOKEN_INVALID`. A valid request made
by an authenticated principal without the required authority returns HTTP 403 with
`ACCESS_DENIED`. Clients may fetch a fresh CSRF token and retry only the former.

## Error shape

Errors use a stable machine-readable code and safe message, with optional validation fields:

```json
{
  "code": "VALIDATION_FAILED",
  "message": "The submitted data is invalid.",
  "fieldErrors": {
    "email": "must be a well-formed email address"
  }
}
```

Do not branch frontend logic on human-readable messages.

## Classroom scope and optional work

Implemented: public events, partial drafts, event invitations, registration/cancellation, participant
lists, check-in, dashboards, event governance, users/roles/permissions, password recovery, and audit activity.
The audit controller also provides `/admin/audit-logs/export`, `/sign-ins`, `/sign-ins/export`,
`/sessions`, and `/sessions/export` beneath `/api/v1`. Exports are Excel workbooks.

Optional future work includes server-stored profile avatars, signed attendance tickets,
individual admin registration cancellation, participant export, and email verification outside the
local profile. Real payments are not required for this university project.

## Contract rules

- UUIDs are serialized as strings.
- Timestamps are ISO-8601 with offsets.
- Large collections are paginated and have a maximum page size.
- Database uniqueness prevents duplicate active registrations; a cancelled registration can be reactivated.
- A retry of mock paid registration resumes the same pending record; no idempotency-key header is required.
- Stale optimistic-lock versions return HTTP 409.
- Administrative mutations produce audit records.
