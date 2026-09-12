--liquibase formatted sql

-- Enum types. Creating a type inside a transaction works without any extra flag.
-- ADDING A VALUE to an enum later is a different matter — see README.md.

--changeset mcp-panel:001-enums dbms:postgresql
--comment: the enum types the panel uses

-- -----------------------------------------------------------------------------
-- Enum tipleri
-- Only what real columns use is an enum. The distinctions inside JSONB — HTTP
-- method, SSH auth method, query mode and so on — stay as plain text and are
-- validated by the application.
-- -----------------------------------------------------------------------------

CREATE TYPE user_role      AS ENUM ('admin', 'developer', 'viewer');

CREATE TYPE user_status    AS ENUM ('active', 'invited', 'suspended');

CREATE TYPE model_provider AS ENUM ('anthropic', 'openai_compatible', 'azure',
                                    'vertex', 'bedrock', 'ollama', 'custom');

CREATE TYPE model_status   AS ENUM ('online', 'degraded', 'offline', 'unchecked');

CREATE TYPE action_kind    AS ENUM ('rest', 'ssh', 'db');

CREATE TYPE log_level      AS ENUM ('info', 'warn', 'error');

CREATE TYPE run_status     AS ENUM ('pending', 'awaiting_approval', 'running',
                                    'succeeded', 'failed', 'cancelled');

CREATE TYPE target_status  AS ENUM ('pending', 'running', 'succeeded',
                                    'failed', 'skipped');

CREATE TYPE secret_kind    AS ENUM ('ssh_private_key', 'ssh_passphrase',
                                    'password', 'api_token', 'model_api_key',
                                    'other');

--rollback DROP TYPE IF EXISTS secret_kind, target_status, run_status, log_level, action_kind, model_status, model_provider, user_status, user_role;
