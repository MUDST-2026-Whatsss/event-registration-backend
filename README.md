# Event Registration API

Spring Boot 4 / Java 17 API for Eventsss, backed by PostgreSQL 18 and Flyway.

## Current scope

Available at runtime:

- Health endpoints
- Register, login, refresh, logout, and current-user authentication
- Cookie and CSRF protection
- Participant profile update, password change, and SMTP password recovery
- Database-backed roles, permissions, sessions, login attempts, and account lockout
- Current UUID event/category persistence model with pageable repository and validation foundation
- Private MinIO object storage with validated event-image upload, public read proxy, and owner-scoped deletion

Administrative event lifecycle, review, audit, and category APIs are active, as are the public
event catalogue (with remaining seats), participant registration and cancellation with capacity
enforcement, and the per-event participant list for administrators. See `docs/api.md` for the
current API contract.

This is a university classroom project. Paid registration uses the existing QR simulation and a
backend mock confirmation (`MOCK_PAYMENTS_ENABLED`, enabled by the `local` profile). No real payment
provider is required. Registration and cancellation still persist in PostgreSQL.

## Requirements

- Java 17
- The included Maven wrapper downloads Maven automatically; a separate Maven installation is optional.
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

Clone both repositories beside each other. Use frontend `develop` to include the latest application
and browser tests, matching the backend CI workflow:

```bash
git clone https://github.com/MUDST-2026-Whatsss/event-registration-backend.git
git clone --branch develop https://github.com/MUDST-2026-Whatsss/event-registration-frontend.git
cd event-registration-backend
```

Copy `.env.example` to `.env` and replace the placeholder secrets as described above. A new local
database is initialized automatically by Flyway; no existing database dump is required. Then run:

```bash
docker compose up -d
docker compose logs -f frontend api
```

Open the frontend at `http://localhost:5173`. It serves the production Vue build through Nginx and
proxies same-origin `/api` requests to the API container. Set `FRONTEND_PORT` when port 5173 is not
available.

For Vite development, the local CORS defaults allow `localhost` and `127.0.0.1` on ports 5173
and 5174. If your existing `.env` overrides `CORS_ALLOWED_ORIGINS`, include the exact URL printed
by `npm run dev`. After changing that variable, recreate the API container with
`docker compose up -d --no-deps api`; restarting alone does not reload Compose environment values.

MinIO API and Console bind to loopback only:

- S3 API: `http://localhost:9000`
- Admin Console: `http://localhost:9001`

The bucket is created lazily by the API on the first upload. Image objects persist in the named
`event_registration_minio_data` volume when containers are recreated. Never delete that volume
without a verified backup.

## Run the local stack against managed PostgreSQL

Set `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, and `DB_SCHEMA=public` in `.env`, then run:

```bash
docker compose -f docker-compose.server.yml up -d --build minio api frontend
docker compose -f docker-compose.server.yml logs -f api
```

This mode does not depend on the local PostgreSQL container. Do not run destructive tests or test
fixtures against a shared/production database.

## Deploy to Kubernetes (Minikube)

Manifests are in `k8s/`:

| File | Objects |
|---|---|
| `namespace.yaml` | Namespace `event-registration` |
| `configmap.yaml` | ConfigMap with non-secret settings |
| `postgres.yaml` | Headless Service + StatefulSet (PVC from `volumeClaimTemplates`) |
| `minio.yaml` | PersistentVolumeClaim + Deployment + ClusterIP Service |
| `deployment.yaml` | API Deployment (2 replicas, RollingUpdate, liveness/readiness probes) |
| `service.yaml` | API NodePort Service on port `30080` |
| `frontend.yaml` | Frontend Deployment (2 replicas) + NodePort Service on `30090`, and Service `api` used by the frontend nginx proxy |
| `ingress.yaml` | Classroom path `/event-registration` and frontend root routes through the Nginx Ingress controller |

The Secret is not stored in Git; create it with `kubectl create secret`.

```bash
minikube start
eval $(minikube docker-env)          # build images inside Minikube
docker build -t event-registration-api:local .
docker build -t event-registration-minio:RELEASE.2025-10-15T17-29-55Z -f docker/minio/Dockerfile .
docker build -t event-registration-frontend:local ../event-registration-frontend   # develop branch

kubectl apply -f k8s/namespace.yaml
kubectl -n event-registration create secret generic event-registration-secret \
  --from-literal=DB_USERNAME=event_app \
  --from-literal=DB_PASSWORD=$(openssl rand -hex 16) \
  --from-literal=JWT_SECRET=$(openssl rand -base64 48) \
  --from-literal=STORAGE_ACCESS_KEY=event_minio_admin \
  --from-literal=STORAGE_SECRET_KEY=$(openssl rand -hex 16)
kubectl apply -f k8s/configmap.yaml -f k8s/postgres.yaml -f k8s/minio.yaml -f k8s/service.yaml
kubectl -n event-registration rollout status statefulset/postgres
sed "s|ghcr.io/mudst-2026-whatsss/event-registration:latest|event-registration-api:local|" k8s/deployment.yaml | kubectl apply -f -
kubectl -n event-registration rollout status deployment/event-registration-api
kubectl apply -f k8s/frontend.yaml
kubectl -n event-registration get pods

# Open the website at http://localhost:8090
kubectl -n event-registration port-forward service/event-registration-frontend 8090:80
```

Open the site through `http://localhost:8090` (or `http://<minikube ip>:30090` on Linux). The API only
accepts sign-in requests from origins listed in `CORS_ALLOWED_ORIGINS` in `k8s/configmap.yaml`; add
any other URL you use there.

For the Week 9 Ingress exercise, after deploying the services:

```bash
minikube addons enable ingress
kubectl -n ingress-nginx rollout status deployment/ingress-nginx-controller --timeout=180s
kubectl apply -f k8s/ingress.yaml
kubectl -n ingress-nginx port-forward service/ingress-nginx-controller 16969:80
```

Open `http://localhost:16969/event-registration/`. The existing frontend navigation and
assets use root URLs on the same origin. The API is available at
`http://localhost:16969/event-registration/api/health` and `/api/health`.
The Ingress structure follows Class Week 8 and the path-routing task on Lab Week 9, page 29.

GitHub Actions deploys automatically on every push to `main`: after tests pass and the image is
pushed to GHCR, the `deploy-kubernetes` job starts Minikube inside the runner and loads the tested
backend image. The build job checks out frontend `develop`, runs unit tests, the build, and Cypress,
and records its commit SHA. Deployment checks out that exact frontend SHA, builds its image,
creates the Secret, applies `k8s/`, and waits for the rollout. Health checks cover the API, frontend
proxy, and classroom Ingress path. Both commit SHAs appear in the Actions run summary.

The CI Minikube cluster is temporary and ends with the runner. It demonstrates deployment for the
course; it does not create a permanent hosted website. Use the commands above for the local demo.

The Compose files build the pinned final MinIO community security release from its upstream Go tag
because that release is not available as a public registry image. The first build takes longer;
later builds use Docker cache. Keep ports 9000/9001 private and expose event images through the API
or a trusted reverse proxy/CDN.

Optional storage round-trip test while Compose MinIO is running:

```bash
set -a
source .env
set +a
RUN_MINIO_IT=true ./mvnw test-compile \
  -Dit.test=MinioObjectStorageLiveIT \
  failsafe:integration-test failsafe:verify
```

## Verify

```bash
./mvnw verify
```

On Windows PowerShell, use `./mvnw.cmd verify`. Set `JAVA_HOME` to a Java 17 JDK and start Docker
Desktop before running either command. The wrapper downloads Maven and dependencies on first use.

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
