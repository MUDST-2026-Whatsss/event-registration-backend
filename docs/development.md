# Backend development workflow

## Published images or source builds

The base `docker-compose.yml` contains images only. After the release CI has published all three
application images and their GHCR packages are public, the instructor can extract the delivery
artifact, keep its generated `.env`, and run `docker compose up -d`. No Java, Node.js, Git, or source
checkout is needed for that mode. The artifact's `IMAGE_TAG` pins a release made from the backend
SHA, workflow run ID, and attempt, so reruns do not replace earlier delivery tags. Backend and
frontend source SHAs are recorded separately in `versions.txt`.

Workflow/package publication is a separate step; uncommitted local changes do not create a release.
To preview candidate images already built/tagged locally, use the preview environment and
`docker compose up -d --pull never`.

## Source build with local database

1. Copy `.env.example` to `.env`.
2. Set strong local database, JWT, and `STORAGE_*` secret values.
3. Clone the frontend on `develop` beside this repository, or set `FRONTEND_DIR`.
4. Build the current source and start the stack:

   ```bash
   docker compose -f docker-compose.yml -f docker-compose.build.yml up -d --build
   ```

5. Follow startup with `docker compose -f docker-compose.yml -f docker-compose.build.yml logs -f api`.

Keep both `-f` arguments when managing source builds. The override adds build contexts and local
image settings; a command using only the base file selects published images instead.

For Vite development, keep the API/storage running and start `npm run dev` in the frontend. Its
`/api` proxy defaults to `http://localhost:8080`; no frontend `.env` is required for that default.
The backend allows localhost/127.0.0.1 on ports 5173/5174 by default. An existing
`CORS_ALLOWED_ORIGINS` override must include the exact Vite URL.

MinIO data survives container recreation in `event_registration_minio_data`. Its S3 API and admin
Console are bound to `127.0.0.1:9000` and `127.0.0.1:9001`; do not expose the Console publicly.
The project name and PostgreSQL/MinIO named volumes remain the same when switching between source
builds and release images. Keep existing secrets and do not use `down -v` to update the app.

A new database has no login accounts. Register a user, then follow the guarded
[first administrator setup](docker-setup.md#first-administrator) once to access administration.
Use the existing administrator screens for later role changes. Actual password-reset emails
require configured SMTP; the local profile supports signup/login without SMTP.

## Managed database mode

Set the ignored `.env` to `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, and `DB_SCHEMA=public`, then run:

```bash
docker compose -f docker-compose.server.yml up -d --build minio api
```

Use managed mode only for intentional manual verification. Automated tests and seed/reset scripts
must use isolated local/Testcontainers databases.

## Tests

```bash
./mvnw verify
```

On Windows PowerShell, run `./mvnw.cmd verify`. Use Java 17 and keep Docker Desktop running.
The included wrapper downloads Maven automatically; no global Maven installation is required.

The build must fail if migrations cannot apply or Hibernate mappings do not match PostgreSQL. Event
tests are included in the normal build. The optional live storage round-trip test is documented in
the root README and runs only when `RUN_MINIO_IT=true`.

### API end-to-end tests (Cypress)

The Cypress suite in `cypress/e2e` calls the real API, so start the local Compose stack first:

```bash
docker compose -f docker-compose.yml -f docker-compose.build.yml up -d --build postgres minio api
npm ci
npm run test:e2e
```

Registration only grants the USER role, so `cy.loginAs` creates fixed `e2e_*@example.com`
accounts and inserts the ADMIN or SUPER_ADMIN role through `docker compose exec postgres psql`.
Run it only against a disposable local database, never a shared or production one. Set
`COMPOSE_PROJECT_NAME` when the stack was started under a different Compose project name. Login
sessions are cached across specs to stay under the login rate limit.

## Coding checklist

- Add request/response DTOs and Jakarta validation.
- Keep business rules and `@Transactional` boundaries in services.
- Enforce permission plus resource scope in the backend.
- Return stable error codes, not database exceptions.
- Do not log cookies, passwords, raw tokens, or personal/payment data.
- Add unit tests and PostgreSQL integration tests for state transitions.
- Add Flyway migrations before relying on a new field or constraint.
- Run `./mvnw verify` (Windows: `./mvnw.cmd verify`) and `git diff --check` before handoff.
