# Database Roles

Use separate PostgreSQL roles for migration, application runtime, and disposable tests.

Do not create or alter these roles automatically during application startup.

## Migration user

The migration user owns schema changes and runs Flyway migrations.

Recommended privileges:

- `CONNECT` on the target database
- `CREATE` on the application schema
- ownership of the schema objects created by migrations

Do not use this role for ordinary application traffic.

## Application user

The application user should only read and modify business data.

It should not have:

- `CREATEDB`
- `CREATEROLE`
- arbitrary `DROP SCHEMA`
- Flyway clean access

Example setup:

```sql
create role clinic_migration login password 'change-me';
create role clinic_app login password 'change-me';

grant connect on database clinic_management to clinic_migration, clinic_app;
grant usage on schema public to clinic_app;
grant select, insert, update, delete on all tables in schema public to clinic_app;
grant usage, select on all sequences in schema public to clinic_app;

alter default privileges in schema public grant select, insert, update, delete on tables to clinic_app;
alter default privileges in schema public grant usage, select on sequences to clinic_app;
```

## Test user

The test user should only be allowed in the disposable test database.

Recommended setup:

```sql
create database clinic_management_test owner clinic_test;
create role clinic_test login password 'change-me';
grant all privileges on database clinic_management_test to clinic_test;
```

Use Testcontainers when possible. If a shared test database is unavoidable, keep it isolated from the runtime database and do not reuse production credentials.
