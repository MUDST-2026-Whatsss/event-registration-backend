# API contract

Base path: `/api/v1`

## Current endpoints

| Method | Path | Access | Status |
| --- | --- | --- | --- |
| GET | `/api/health` | Public | Active legacy health probe |
| GET | `/actuator/health` | Public | Active operational health probe |
| GET | `/api/v1/auth/csrf` | Public | Active |
| POST | `/api/v1/auth/register` | Public, rate-limited | Active |
| POST | `/api/v1/auth/login` | Public, rate-limited | Active |
| POST | `/api/v1/auth/refresh` | Refresh cookie | Active |
| POST | `/api/v1/auth/select-role` | Authenticated | Active; issues an access token scoped to one assigned role |
| POST | `/api/v1/auth/logout` | Public/idempotent | Active |
| GET | `/api/v1/auth/me` | Authenticated | Active |
| PATCH | `/api/v1/auth/me` | Authenticated | Active |
| POST | `/api/v1/auth/change-password` | Authenticated | Active |
| GET | `/api/v1/event-categories` | Public | Active |
| GET | `/api/v1/admin/events` | Assigned ADMIN or SUPER_ADMIN | Active, paginated |
| GET | `/api/v1/admin/events/stats` | Assigned ADMIN or SUPER_ADMIN | Active |
| POST | `/api/v1/admin/events` | `EVENT_CREATE` or SUPER_ADMIN | Active |
| GET | `/api/v1/admin/events/{eventId}` | Assigned ADMIN or SUPER_ADMIN | Active |
| PATCH | `/api/v1/admin/events/{eventId}?version=` | `EVENT_UPDATE`, assigned scope | Active for DRAFT/REJECTED |
| POST | `/api/v1/admin/events/{eventId}/submit?version=` | `EVENT_UPDATE`, assigned scope | Active |
| POST | `/api/v1/admin/events/{eventId}/withdraw?version=` | `EVENT_UPDATE`, assigned scope | Active |
| POST | `/api/v1/admin/events/{eventId}/cancel` | `EVENT_CANCEL`, assigned scope | Active |
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
| GET | `/api/v1/admin/users/{userId}` | SUPER_ADMIN | Active |
| PATCH | `/api/v1/admin/users/{userId}/status` | SUPER_ADMIN | Active; disabling revokes sessions |
| PUT | `/api/v1/admin/users/{userId}/roles` | SUPER_ADMIN | Active; replaces assigned role set and revokes existing sessions |
| GET | `/api/v1/admin/roles` | SUPER_ADMIN | Active; includes permissions and usage count |
| POST | `/api/v1/admin/roles` | SUPER_ADMIN | Active; creates custom role |
| PATCH | `/api/v1/admin/roles/{roleId}` | SUPER_ADMIN | Active; revokes sessions using the role |
| PUT | `/api/v1/admin/roles/{roleId}/permissions` | SUPER_ADMIN | Active; revokes sessions using the role |

Multi-role login returns all assigned role codes but no active permissions until the client calls
`POST /auth/select-role`. Access tokens contain only the selected role and its permissions. The
client includes the selected role when refreshing so a rotated access token keeps the same scope.
| GET | `/api/v1/admin/permissions` | SUPER_ADMIN | Active |
| GET | `/api/v1/admin/audit-logs` | SUPER_ADMIN | Active; immutable paginated history |

The legacy `/api/events` controller has been removed. Public event list/detail controllers remain
planned for `/api/v1/events`; event categories and the assigned Admin lifecycle are active.

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

## Planned endpoint groups

- Public catalogue: categories, event search/list, event detail
- Admin event lifecycle is active; dashboard-specific trends and registration totals remain planned
- Super-admin event governance, users, roles, permissions, and audit history are active
- Registrations: create, list mine, detail, cancel, QR
- Event operations: participant list, admin cancellation, check-in, CSV export
- Administration still planned: invitations and aggregate dashboard endpoints
- Auth completion: forgot/reset password, email verification, invitation acceptance
- Payment: disabled provider abstraction first, verified provider/webhook integration later

The endpoint-by-endpoint implementation order is maintained in the workspace `PLAN.md`.

## Contract rules

- UUIDs are serialized as strings.
- Timestamps are ISO-8601 with offsets.
- Large collections are paginated and have a maximum page size.
- Create-registration/payment operations require an idempotency key.
- Stale optimistic-lock versions return HTTP 409.
- Administrative mutations produce audit records.
