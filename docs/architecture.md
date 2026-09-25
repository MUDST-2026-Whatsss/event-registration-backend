# Backend architecture

## Runtime layers

```text
HTTP request
  -> rate-limit filter
  -> JWT authentication filter
  -> Spring Security authorization and CSRF
  -> controller + validated DTO
  -> transactional domain service
  -> Spring Data repository
  -> PostgreSQL
```

Event cover upload/read flow:

```text
authenticated ADMIN/SUPER_ADMIN
  -> multipart upload endpoint
  -> byte signature, size, and dimension validation
  -> provider-neutral ObjectStorage boundary
  -> private MinIO bucket + persistent Docker volume
  -> object key stored with the event
  -> public immutable image URL proxied by the API
```

Controllers own HTTP concerns only. Services own lifecycle, permission scope, concurrency, and
transaction boundaries. Repositories own persistence queries. Responses use DTOs rather than JPA
entities.

## Modules

| Module | Status | Responsibility |
| --- | --- | --- |
| `auth` | Active | Accounts, participant profile, cookies, JWT, sessions, roles/permissions, lockout, and rate limiting |
| `health` | Active | Lightweight and Actuator health checks |
| `common` | Active | JSON configuration and stable error responses |
| `event` | Active foundation | UUID entities, lifecycle enums, category/creator mappings, validated DTOs, and pageable repositories; public controllers are next |
| `storage` | Active | MinIO client, private bucket, event-image validation, upload/read/delete, and URL resolution |
| `registration` | Planned | Capacity, registration lifecycle, cancellation, QR, and check-in |
| `payment` | Planned scaffold | Provider abstraction and verified payment state |
| `administration` | Planned | Review, change request, assignment, user, and role operations |
| `audit` | Planned service/API | Append-only administrative audit records |
| `notification` | Planned | Transactional outbox and email delivery |

## Security model

- Access and refresh tokens are sent only through HTTP-only cookies.
- The access token is stateless; refresh-token hashes and rotation families are stored in PostgreSQL.
- Unsafe browser requests require the `X-XSRF-TOKEN` header matching the CSRF cookie.
- Roles provide scope (`SELF`, `ASSIGNED_EVENTS`, `GLOBAL`) and permissions grant operations.
- Backend services must enforce both permission and data scope; frontend route guards are not a
  security boundary.
- New routes are denied by default unless explicitly public or authenticated.
- Event-image writes require ADMIN/SUPER_ADMIN. An ADMIN may delete only objects under their own
  UUID prefix; SUPER_ADMIN may remove any event image.
- MinIO credentials and Console are private infrastructure. Browser clients never receive storage
  credentials and the bucket has no anonymous policy.
- When storage is enabled, Actuator health reports DOWN if MinIO cannot be reached.

## Concurrency model

Use optimistic locking for normal edits and PostgreSQL row locks for state transitions that must be
serialized. Event capacity uses event-row locking, idempotency, unique constraints, and expiring
seat holds. An external message queue is not required for first-release capacity control.

## Time and identifiers

- Domain identifiers are UUIDs.
- Database timestamps use `timestamp with time zone`.
- Persist absolute times in UTC and retain an IANA event timezone for display.
- API timestamps use ISO-8601 offsets.
