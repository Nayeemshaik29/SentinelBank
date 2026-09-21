-- One database, one schema per service: the schemas are the "database per service" boundary.
-- Each service connects with ?currentSchema=<name> and runs its own Flyway migrations there.
CREATE SCHEMA IF NOT EXISTS auth;
CREATE SCHEMA IF NOT EXISTS account;
CREATE SCHEMA IF NOT EXISTS transaction;
CREATE SCHEMA IF NOT EXISTS partner_bank;
CREATE SCHEMA IF NOT EXISTS notification;
