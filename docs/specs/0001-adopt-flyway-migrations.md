# 0001. Adopt Flyway for database migrations

**Date**: 2026-10-01
**Status**: Accepted

## Summary

Leadfold will stop letting Hibernate (the JPA tool that maps Java classes to tables) change the database on its own, and will use Flyway instead. Flyway runs plain SQL files in order, once each, and remembers which ones ran. The current four tables become the first file (`V1__baseline.sql`), and Hibernate is switched to only check that the tables match the code. Migrations run when the app starts, through Neon's direct connection, and you try each change on a Neon branch before the main database.

## Context

The project runs `spring.jpa.hibernate.ddl-auto: update`. Hibernate compares the entities to the database at every start and adds missing tables and columns. It never drops or renames anything, never changes a column type safely, never backfills data, and leaves no record of what changed or when. That is fine while the only data is throwaway test data. It becomes a real risk the moment a paying account exists, because one renamed field silently adds a second column and strands the old data.

The schema today is four tables (`users`, `leads`, `whatsapp_configs`, `zapier_subscriptions`) with UUID keys and no test suite. The database is one Neon Postgres instance, reached through `DATABASE_URL`. Neon offers a pooled connection (PgBouncer in front) and a direct connection, and session level locks can misbehave on the pooled one. The founder works alone on roughly 10 to 15 hours a week, so the solution must need no extra infrastructure and no new concepts beyond SQL.

CLAUDE.md already records the intent: replace `ddl-auto: update` with Flyway or Liquibase before real user data exists. This spec makes that call.

No spec or scope row existed for this, so it is a standalone decision. No prerequisite decision is missing.

## Requirements

**User stories**:
- As the founder, I want every schema change to be a reviewed SQL file in git so that I can see what changed and repeat it on any database.
- As the founder, I want the app to refuse to start when the code and database disagree so that a forgotten migration shows up at boot, not as a runtime error for a customer.

**Acceptance criteria** (the contract, each one independently checkable):
- **AC-1**: On an empty Postgres database, starting the app applies `V1__baseline.sql` and creates the tables `users`, `leads`, `whatsapp_configs`, `zapier_subscriptions` with the keys, unique constraints, foreign keys and the `idx_leads_user_created` index listed in the data model, plus Flyway's `flyway_schema_history` table. Constraints and the index are confirmed by a manual `\d+ <table>` check in `psql` or the Neon SQL editor, because `validate` does not check them.
- **AC-2**: Starting the app a second time against the same database applies no migration and boots normally.
- **AC-3**: Hibernate runs with `ddl-auto: validate`. The app boots against the migrated schema. If an entity has a column or table the database lacks, or a column of an incompatible type, the app fails to start with a schema validation error. (`validate` does not check nullability, lengths, unique constraints, foreign keys or indexes.)
- **AC-4**: Migrations connect using `DATABASE_DIRECT_URL`. The running app keeps using `DATABASE_URL`. When `DATABASE_DIRECT_URL` is not set, migrations fall back to `DATABASE_URL`.
- **AC-5**: No configuration file or profile sets `ddl-auto` to `update`, `create`, or `create-drop`.
- **AC-6**: The README explains how to add a migration, including the rule that an applied migration file is never edited.
- **AC-7**: The baseline has been run and validated on a Neon branch before the main Neon database is reset and migrated.

## Options considered

### Option 1: Flyway with plain SQL files

Flyway is a migration runner. You write SQL in `V<number>__<name>.sql`, and Flyway runs each new file once, in order, recording it in a history table. Spring Boot runs it automatically at startup.

**Pros**:
- Only SQL to learn, and the project is Postgres only.
- Smallest setup. Spring Boot manages the version and starts it with no extra code.
- Each file is a readable diff in git.

**Cons**:
- Undo scripts (rollback files) are a paid feature, so you roll forward with a new file instead.
- SQL is Postgres specific, so a future move to another database means rewriting the files.

### Option 2: Liquibase with YAML or XML changelogs

Liquibase describes changes in a database neutral format and supports rollback instructions.

**Pros**:
- Database neutral changes and built in rollback support.
- Stronger tooling for diffing and preconditions.

**Cons**:
- More concepts and more files for one Postgres app.
- Changes are written in an abstraction you then have to translate to SQL in your head when debugging.

### Option 3: Keep `ddl-auto: update`

Do nothing and revisit later.

**Pros**:
- Zero work now.

**Cons**:
- Renames and type changes are silently wrong or ignored, and there is no history. The cost of fixing it rises with every customer row.

## Decision

**Chosen option**: Option 1: Flyway with plain SQL files

Migrations are plain SQL files run by Flyway at app startup over Neon's direct connection, and Hibernate is set to `validate` only.

## Rationale

The founder's time is the scarcest force in Context, and Flyway asks for the least of it: SQL files and one dependency, with Spring Boot doing the wiring. Liquibase's extra power (database neutrality, rollback) solves problems a single Postgres app does not have, and CLAUDE.md already chose Postgres on Neon as settled. Keeping `update` was rejected because the cost of switching is lowest now, while the only data is test data.

Running at startup fits a solo founder with one app instance and no deploy pipeline. Flyway takes a database lock so two instances starting together cannot apply a migration twice. The lock is why migrations use the direct Neon connection: the pooled one can break session level locks. Runner up for timing is a separate migration step in the deploy, which is safer at larger scale and can be adopted later without changing the SQL files.

`validate` (over `none`) makes drift loud at boot for missing tables, missing columns and wrong types. It does not catch missing constraints or indexes, so it is a partial safety net. With no test suite in the repo it is still the main automatic one, so it earns its place.

The engineer chose to verify migrations by hand on a Neon branch instead of a Testcontainers test. That is cheaper and needs no Docker, but it depends on discipline. The Consequences section names this cost.

## Feature design

**Data model sketch** (the baseline, `V1__baseline.sql`, frozen to match today's entities exactly; Spring's default naming turns camelCase into snake_case):

| Table | Columns | Keys and rules |
|---|---|---|
| `users` | `id` uuid, `email` varchar(255), `password_hash` varchar(255), `api_key` varchar(255), `company_name` varchar(255) null, `vertical` varchar(255) null, `created_at` timestamp(6) with time zone | PK `id`; unique `email`; unique `api_key`; all listed not null except the two marked null |
| `leads` | `id` uuid, `user_id` uuid, `source` varchar(255), `raw_message` text, `name` varchar(255) null, `contact` varchar(255) null, `interest_summary` text null, `extracted_fields_json` text null, `status` varchar(255), `created_at` timestamp(6) with time zone | PK `id`; FK `user_id` to `users(id)`; index `idx_leads_user_created` on (`user_id`, `created_at`); not null except the three marked null |
| `whatsapp_configs` | `id` uuid, `user_id` uuid, `phone_number_id` varchar(255), `access_token` varchar(255), `created_at` timestamp(6) with time zone | PK `id`; FK `user_id` to `users(id)`; unique `phone_number_id`; all not null |
| `zapier_subscriptions` | `id` uuid, `user_id` uuid, `target_url` varchar(255), `event` varchar(255), `created_at` timestamp(6) with time zone | PK `id`; FK `user_id` to `users(id)`; all not null |

No default values are set in SQL. The app supplies `id` (Hibernate UUID), `created_at`, and the default `status` ("new") and `event` ("new_lead"). Foreign keys have no `ON DELETE` clause, matching what Hibernate does today. Always use `timestamp(6) with time zone` for timestamps, never plain `timestamp`, or `validate` fails or times shift.

Constraint names (use exactly these): primary keys `pk_users`, `pk_leads`, `pk_whatsapp_configs`, `pk_zapier_subscriptions`; unique `uk_users_email`, `uk_users_api_key`, `uk_whatsapp_configs_phone_number_id`; foreign keys `fk_leads_user`, `fk_whatsapp_configs_user`, `fk_zapier_subscriptions_user`; index `idx_leads_user_created`.

**How `V1` is produced**: do not write it from scratch. Generate the DDL once from Hibernate (set `spring.jpa.properties.jakarta.persistence.schema-generation.scripts.action=create` and `...scripts.create-target=target/v1.sql` for one run against a scratch database), then copy it into `V1__baseline.sql`, rename the constraints to the names above, and remove the generation properties. Compare the result to the table above before committing.

The `@Index` annotation on `Lead` stays in the entity as documentation. `validate` ignores indexes.

No extra foreign key indexes are added in this work (engineer's call). Add them in a later `V2` if those tables grow.

**State transitions**: not applicable.

**API surface**: none. No endpoint changes.

**Value sourcing**:
| Action | Value produced / displayed | Source |
|---|---|---|
| Flyway migrate at startup | Database to migrate | `DATABASE_DIRECT_URL`, else `DATABASE_URL` |
| Flyway migrate at startup | Database user and password | `DATABASE_USERNAME` and `DATABASE_PASSWORD` (Neon uses the same credentials on the pooled and direct hosts) |
| Flyway migrate at startup | Which files to run | `classpath:db/migration`, files named `V<n>__<name>.sql` |
| App runtime | Database connection | `DATABASE_URL` (pooled), unchanged |
| Hibernate validate | Expected schema | The JPA entities in `com.leadfold.entity` |

**Key invariants**:
- An applied migration file is never edited or renamed. Flyway stores a checksum, and a changed file stops the app at start.
- Every entity change ships with a new migration file in the same commit. Migrations only move forward; to undo a change, write a new file.
- Hibernate never changes the schema, in any environment.

**Security model**: The migration connection uses the same database owner user as the app, because Flyway needs to create tables. There is no new credential type. `DATABASE_DIRECT_URL` holds the same secret material as `DATABASE_URL`, so it is handled the same way (env var, never committed). No regulated data is added or moved by this work.

**Configuration required**:
- `DATABASE_DIRECT_URL`: Neon direct connection string in JDBC form (`jdbc:postgresql://...`), the host without `-pooler` in its name. Optional, falls back to `DATABASE_URL`. It must be either unset or a full URL, never blank: a blank value counts as set and breaks the fallback. In `.env.example` add it as a commented out line (`# DATABASE_DIRECT_URL=jdbc:postgresql://ep-xxxx.<region>.aws.neon.tech/...`). Your current `DATABASE_URL` may already be the direct host (no `-pooler` in it). If so, the variable adds nothing today, but keep it so the app can move to the pooled URL later without touching migrations. Moving the running app to the pooled URL is a separate decision, not part of this spec.
- `application.yml`: `spring.jpa.hibernate.ddl-auto: validate`; `spring.flyway.enabled: true`; `spring.flyway.url: ${DATABASE_DIRECT_URL:${DATABASE_URL}}`; `spring.flyway.user: ${DATABASE_USERNAME}`; `spring.flyway.password: ${DATABASE_PASSWORD}`. Leave `baseline-on-migrate` off (the database is reset, see the migration plan).
- `pom.xml`: add `org.flywaydb:flyway-core` and `org.flywaydb:flyway-database-postgresql` (Flyway 10 and later needs the separate Postgres module). Do not set a version; Spring Boot 3.3.4 manages it.

**Failure and edge cases**:
- A migration fails midway: Postgres runs DDL (table and index changes) inside a transaction, and Flyway wraps each file in one, so the change rolls back and the app refuses to start. Fix by writing a corrected new file. If the failed file never applied, editing it is safe.
- Two instances start together: Flyway's lock makes one wait. This works on the direct connection.
- Neon scales the database to zero when idle, so the first connection at boot can take a few seconds. A slow first start is expected, not a failure.
- Flyway 10.10 (the version Spring Boot 3.3.4 manages) may log a warning that your Postgres version is newer than it has tested (Neon defaults to a recent one). That is a warning, not a failure.
- Checksum mismatch after someone edits an applied file: the app stops at start with a Flyway validation error. Restore the file from git and write a new migration instead.

**Observability**: Flyway logs each applied migration at startup. `flyway_schema_history` in the database is the record of what ran and when. No new metrics or alerts.

**Critical test scenarios** (run by hand on a Neon branch, since no test suite exists):
- Happy path: point the app at an empty branch, start it, check the four tables, constraints, index and `flyway_schema_history` exist, verifies **AC-1**
- Repeat start: start the app again, no migration applied, boots, verifies **AC-2**
- Drift: temporarily add a field to one entity without a migration, start the app, it fails with a validation error, then revert the field, verifies **AC-3**
- Connection: with `DATABASE_DIRECT_URL` set, the log shows Flyway on the direct host; unset it, migrations still run via `DATABASE_URL`, verifies **AC-4**
- Config check: search the repo for `ddl-auto` and confirm only `validate` remains, verifies **AC-5**

## Build plan

The project records no build approach, so this assumes the default: one thin end to end slice. The change is small enough to ship as one slice, then a verification and a destructive reset that only you run.

1. You check the host in your current `DATABASE_URL` in the Neon dashboard (pooled has `-pooler`, direct does not) and copy the direct URL, satisfies **AC-4**
2. Add `flyway-core` and `flyway-database-postgresql` to `pom.xml` (no version), satisfies **AC-1**
3. Produce `src/main/resources/db/migration/V1__baseline.sql` by generating the DDL from Hibernate as described above, renaming the constraints, and checking it against the data model, satisfies **AC-1**, **AC-2**
4. Update `application.yml`: set `ddl-auto: validate`, add the `spring.flyway.*` block with the `DATABASE_DIRECT_URL` fallback. Add the commented out `DATABASE_DIRECT_URL` line to `.env.example`, satisfies **AC-3**, **AC-4**, **AC-5**
5. Update the README: rewrite the line that says Hibernate auto creates tables with `ddl-auto: update`, add a "Changing the database" section (how to add a migration, never edit an applied file, forward only, how to get the Neon direct URL), and remove the migrations item from "Not built yet", satisfies **AC-6**
6. Verify on a Neon branch: create a branch of the current database, reset it (see the migration plan), start the app and run the five test scenarios above plus the `\d+` constraint check, satisfies **AC-1**, **AC-2**, **AC-3**, **AC-4**, **AC-7**
7. Only after step 6 passes, reset the main Neon database and start the app once so it applies `V1`, satisfies **AC-7**

## Migration plan

**Strategy**: no migration needed for live data. The database holds only throwaway test data, so it is reset once and rebuilt from `V1`.
**Phases**:
1. On a Neon branch, run `DROP TABLE IF EXISTS leads, whatsapp_configs, zapier_subscriptions, users CASCADE;` in the Neon SQL editor, then start the app and verify (build plan step 6). This drops only the four tables and leaves the `public` schema, its owner and its permissions alone, so Flyway can still create tables as the app user.
2. On the main Neon database, run the same statement, then start the app so Flyway applies `V1` (build plan step 7).
**Rollback**: there is nothing to restore, because no data worth keeping exists. If `V1` fails on the main database, fix the SQL file and reset again. Neon also keeps point in time history if you want an extra safety net before the reset.
**Risks**: the `DROP TABLE` statement deletes all data in those four tables. `/develop` must not run it; you run it by hand, on the right database, after confirming which one the SQL editor is connected to. If real data has appeared in the main database by then, stop and use a baseline on migrate approach instead (supersede this spec).

## Consequences

**Positive**:
- Every schema change is a reviewed SQL file with history, repeatable on any database.
- Drift between code and database fails at boot instead of in front of a customer.
- Adding a separate dev database later needs no change to the migration files.

**Negative / tradeoffs**:
- No automated migration test. A bad migration is found on a Neon branch only if you remember to try it there. A Testcontainers test is the natural upgrade once real customers exist.
- Forward only. Reverting a change means writing a new migration, not running an undo.
- One more env var to set on every host (optional, but needed on Neon to avoid the pooled lock problem).
- Every entity edit now needs a SQL file too, which adds a small step to every data change.

**Neutral**:
- A `flyway_schema_history` table appears in the database.
- Startup runs migration checks each boot, adding a little time to start.
- Migrating at startup means a bad migration blocks the app from starting. That is intended.

## Follow-up

- [ ] Split Neon into a dev branch and the main database soon, so you try every migration on the branch first as a habit
- [ ] When the first real customer arrives, add a Testcontainers migration test (needs Docker) and revisit running migrations as a deploy step
- [ ] Consider foreign key indexes on `whatsapp_configs.user_id` and `zapier_subscriptions.user_id` as a `V2` once those tables grow
- [ ] `CLAUDE.md` "Tech stack" bullet about `ddl-auto: update` is now out of date. `/sync` should update it (this spec does not edit CLAUDE.md)
- [ ] A root `AGENTS.md` does not exist yet. Consider running `/audit` so these migration rules live where every later task reads them
