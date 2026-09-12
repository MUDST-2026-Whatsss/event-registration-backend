# Backend development workflow

## Local database mode

1. Copy `.env.example` to `.env`.
2. Set strong local `POSTGRES_PASSWORD` and `JWT_SECRET` values.
3. Run `docker compose up -d`.
4. Follow startup with `docker compose logs -f api`.

## Managed database mode

Set the ignored `.env` to `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, and `DB_SCHEMA=public`, then run:

```bash
docker compose -f docker-compose.server.yml up -d api
```

Use managed mode only for intentional manual verification. Automated tests and seed/reset scripts
must use isolated local/Testcontainers databases.

## Tests

```bash
mvn verify
```

The build must fail if migrations cannot apply or Hibernate mappings do not match PostgreSQL.
Event tests are temporarily excluded only until the legacy event mapping is replaced; removing those
exclusions is Phase 0 in the workspace plan.

## Coding checklist

- Add request/response DTOs and Jakarta validation.
- Keep business rules and `@Transactional` boundaries in services.
- Enforce permission plus resource scope in the backend.
- Return stable error codes, not database exceptions.
- Do not log cookies, passwords, raw tokens, or personal/payment data.
- Add unit tests and PostgreSQL integration tests for state transitions.
- Add Flyway migrations before relying on a new field or constraint.
- Run `mvn verify` and `git diff --check` before handoff.
