-- Account creation is serialized per JVM, but a second app instance can race it.
-- PostgreSQL enforces uniqueness across all nodes and mixed-case legacy values.
-- Deployments with preexisting duplicate addresses must resolve them before V2 runs;
-- failing closed is safer than silently choosing an account.
CREATE UNIQUE INDEX ux_users_email_case_insensitive ON users (lower(email));
