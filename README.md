# Event Registration API

Spring Boot 4 / Java 17 API for Eventsss, backed by PostgreSQL 18 and Flyway.

## Current scope

Available at runtime:

- Health endpoints
- Register, login, refresh, logout, and current-user authentication
- Cookie and CSRF protection
- Participant profile update and password change
- Database-backed roles, permissions, sessions, login attempts, and account lockout
- Current UUID event/category persistence model with pageable repository and validation foundation
- Private MinIO object storage with validated event-image upload, public read proxy, and owner-scoped deletion

The event persistence foundation now validates against the Flyway v8 schema. Public catalogue and
administrative event controllers are the next API slices; event and registration HTTP endpoints are
not active yet. See the workspace `PLAN.md` and `docs/api.md`.

## Requirements

- Java 17
- Maven 3.9+
- Docker Desktop for the standard local environment and Testcontainers tests

## Configuration

Copy `.env.example` to the ignored `.env` and replace every placeholder secret. Important values:

| Variable | Purpose |
| --- | --- |
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | Direct/managed PostgreSQL connection |
| `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD` | Local Compose PostgreSQL |
| `DB_SCHEMA` | Application schema; normally `public` |
| `JWT_SECRET` | HS256 signing secret, at least 32 decoded bytes |
| `CORS_ALLOWED_ORIGINS` | Exact credentialed browser origins |
| `COOKIE_SECURE`, `COOKIE_SAME_SITE`, `COOKIE_DOMAIN` | Authentication cookie policy |
| `STORAGE_ACCESS_KEY`, `STORAGE_SECRET_KEY` | Private MinIO credentials |
| `STORAGE_BUCKET` | Bucket containing event images; defaults to `event-registration` |
| `STORAGE_MAX_IMAGE_SIZE`, `STORAGE_MAX_IMAGE_WIDTH`, `STORAGE_MAX_IMAGE_HEIGHT` | Server-side image limits |

Never commit `.env`. Production values belong in deployment secrets.

## Run with local PostgreSQL

Use the `POSTGRES_*` values from `.env.example`, then run:

```bash
docker compose up -d
docker compose logs -f api
```

MinIO API and Console bind to loopback only:

- S3 API: `http://localhost:9000`
- Admin Console: `http://localhost:9001`

The bucket is created lazily by the API on the first upload. Image objects persist in the named
`event_registration_minio_data` volume when containers are recreated. Never delete that volume
without a verified backup.

## Run the local API against managed PostgreSQL

Set `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, and `DB_SCHEMA=public` in `.env`, then run:

```bash
docker compose -f docker-compose.server.yml up -d --build minio api
docker compose -f docker-compose.server.yml logs -f api
```

This mode does not depend on the local PostgreSQL container. Do not run destructive tests or test
fixtures against a shared/production database.

The Compose files build the pinned final MinIO community security release from its upstream Go tag
because that release is not available as a public registry image. The first build takes longer;
later builds use Docker cache. Keep ports 9000/9001 private and expose event images through the API
or a trusted reverse proxy/CDN.

Optional storage round-trip test while Compose MinIO is running:

```bash
set -a
source .env
set +a
RUN_MINIO_IT=true mvn test-compile \
  -Dit.test=MinioObjectStorageLiveIT \
  failsafe:integration-test failsafe:verify
```

## Verify

```bash
mvn verify
```

The integration suite uses an isolated PostgreSQL 18.6 Testcontainer. H2 is intentionally not used
because the schema relies on PostgreSQL types, indexes, and constraints.

Health checks:

```bash
curl http://localhost:8080/api/health
curl http://localhost:8080/actuator/health
```

## Database migrations

- Flyway is the only schema owner; Hibernate uses `ddl-auto=validate`.
- A new database starts at `B6__current_schema_baseline.sql`, then applies V7 and later.
- Never edit or rename an applied migration.
- `DB_init.sql` and `DB_proposal_v2.sql` are historical, incompatible SERIAL-based proposals and
  must not initialize current environments.
- Add every future change as the next numbered migration.

V8 adds PostgreSQL table/column documentation without changing business data.

## Documentation

- [`docs/architecture.md`](docs/architecture.md): backend modules and request flow
- [`docs/api.md`](docs/api.md): current API contract and endpoint status
- [`docs/database.md`](docs/database.md): schema ownership and table dictionary
- [`docs/development.md`](docs/development.md): development and verification workflow
- [`docs/docker-setup.md`](docs/docker-setup.md): clone-to-running Docker setup and database backup/restore
- [`src/main/resources/db/migration/README.md`](src/main/resources/db/migration/README.md): migration history notes
