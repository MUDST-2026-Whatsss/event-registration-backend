# Database documentation

## Ownership

PostgreSQL 18 is the source of truth. Flyway owns schema changes and Hibernate validates mappings
without creating or altering tables.

```text
B6__current_schema_baseline.sql  new-database baseline at schema version 6
V7__harden_auth_session_rotation.sql
V8__document_database_schema.sql
```

Do not execute `DB_init.sql` or `DB_proposal_v2.sql` against current environments; they describe an
older SERIAL-based design.

## Table groups

| Area | Tables |
| --- | --- |
| Accounts | `auth_users`, `participants` |
| Authorization | `auth_roles`, `auth_permissions`, `auth_role_permissions`, `auth_user_roles` |
| Auth security | `auth_sessions`, `auth_login_attempts`, `auth_action_tokens`, `auth_invitations` |
| Events | `event_categories`, `events`, `event_admin_assignments` |
| Governance | `event_reviews`, `event_change_requests`, `event_change_request_items`, `audit_logs` |
| Registration | `registrations`, `registration_status_history`, `seat_reservations` |
| Payment scaffold | `payment_orders` |

## Field descriptions

V8 stores descriptions for all 21 application tables and all 237 columns in PostgreSQL metadata.
Tools such as pgAdmin and DBeaver display these comments automatically.

Inspect table comments:

```sql
SELECT c.relname AS table_name,
       obj_description(c.oid, 'pg_class') AS description
FROM pg_class c
JOIN pg_namespace n ON n.oid = c.relnamespace
WHERE n.nspname = 'public'
  AND c.relkind = 'r'
ORDER BY c.relname;
```

Inspect column comments:

```sql
SELECT c.table_name,
       c.column_name,
       col_description(format('%I.%I', c.table_schema, c.table_name)::regclass,
                       c.ordinal_position) AS description
FROM information_schema.columns c
WHERE c.table_schema = 'public'
  AND c.table_name <> 'flyway_schema_history'
ORDER BY c.table_name, c.ordinal_position;
```

## Schema rules

- Primary business identifiers are UUIDs generated with `gen_random_uuid()`.
- Passwords, refresh tokens, action tokens, reservation tokens, and QR secrets are stored only as
  one-way hashes.
- State values are constrained in PostgreSQL as well as in Java.
- `version` columns support optimistic locking.
- Registration capacity uses unique constraints and expiring seat holds.
- Historical records are retained; event/registration cancellation uses states rather than hard
  deletion.
- Monetary values use `numeric`, never floating point.
- JSONB metadata must remain structured and must not contain secrets or payment-card data.

## Migration workflow

1. Create the next immutable `V{n}__description.sql` file.
2. Test from an empty baseline and by upgrading a copy of the current schema.
3. Run `mvn verify` with PostgreSQL available.
4. Review destructive/locking impact before applying to a shared database.
5. Update this document and PostgreSQL comments whenever fields change.
