--liquibase formatted sql

-- Enum tipleri. Tip oluşturma transaction içinde sorunsuz çalışır, ek bayrak
-- gerekmez. İleride bir enum'a DEĞER EKLERKEN durum farklı — bkz. README.md.

--changeset mcp-panel:001-enums dbms:postgresql
--comment: Panelin kullandığı enum tipleri

-- -----------------------------------------------------------------------------
-- Enum tipleri
-- Yalnızca gerçek kolonlarda kullanılanlar enum. JSONB içindeki ayrımlar
-- (http metodu, ssh kimlik yöntemi, sorgu biçimi …) düz metin olarak durur ve
-- uygulama tarafında doğrulanır.
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
