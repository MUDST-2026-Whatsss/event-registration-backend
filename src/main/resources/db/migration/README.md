# Database migrations

`V6__complete_event_administration.sql` targets the deployed PostgreSQL/Flyway V5 schema that uses UUID identifiers.

`B6__current_schema_baseline.sql` is the canonical baseline for a brand-new
database. Flyway selects it instead of replaying V1-V5, then applies V7 and later
migrations normally.

`V8__document_database_schema.sql` adds PostgreSQL comments for all 21 application
tables and all 237 columns. It changes metadata only and does not modify business
data. Keep it aligned with `docs/database.md` as later migrations add or rename fields.

Important:

- The local database originally recorded migrations V1 through V5, whose source
  files were lost. Its history was repaired after B6 was created.
- Do not renumber V6 and do not recreate V1 through V5 from `DB_init.sql`.
- `DB_init.sql` and `DB_proposal_v2.sql` use an older SERIAL-based proposal and are incompatible with the deployed UUID schema.
- Apply all future schema changes through Flyway only.

V6 has been applied to the local database and validated against a temporary clone
of V5. B6 has also been migrated and validated successfully on an empty PostgreSQL
18.6 database through Flyway 12.4.0.
