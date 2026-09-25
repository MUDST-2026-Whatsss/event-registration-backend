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
- Super-admin governance: review decisions, change requests, event-admin assignments
- Registrations: create, list mine, detail, cancel, QR
- Event operations: participant list, admin cancellation, check-in, CSV export
- Administration: users, roles, permissions, invitations, audit logs, dashboards
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
