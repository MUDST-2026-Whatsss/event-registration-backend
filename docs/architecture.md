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

Controllers own HTTP concerns only. Services own lifecycle, permission scope, concurrency, and
transaction boundaries. Repositories own persistence queries. Responses use DTOs rather than JPA
entities.

## Modules

| Module | Status | Responsibility |
| --- | --- | --- |
| `auth` | Active | Accounts, participant profile, cookies, JWT, sessions, roles/permissions, lockout, and rate limiting |
| `health` | Active | Lightweight and Actuator health checks |
| `common` | Active | JSON configuration and stable error responses |
| `event` | Temporarily disabled | Must be remapped from legacy numeric IDs to the current UUID schema |
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

## Concurrency model

Use optimistic locking for normal edits and PostgreSQL row locks for state transitions that must be
serialized. Event capacity uses event-row locking, idempotency, unique constraints, and expiring
seat holds. An external message queue is not required for first-release capacity control.

## Time and identifiers

- Domain identifiers are UUIDs.
- Database timestamps use `timestamp with time zone`.
- Persist absolute times in UTC and retain an IANA event timezone for display.
- API timestamps use ISO-8601 offsets.
