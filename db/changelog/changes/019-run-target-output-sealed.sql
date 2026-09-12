--liquibase formatted sql

--changeset mcp-panel:019-run_target_output_sealed dbms:postgresql
--comment: run output, stored sealed

-- result_rows, added in 017, wrote the rows a query returned exactly as they came, and
-- stdout_excerpt kept the same rows as aligned text. That turned the control plane's
-- database into a copy of the production data it queries: PIN, national id, email and
-- name fields accumulated here in the clear.
--
-- Output is now sealed through mcp-cipher and written as one envelope; the key lives
-- outside this database. Text and rows travel in the same envelope, because they are the
-- same answer and sealing them apart would mean two round trips to read one result.
ALTER TABLE run_targets ADD COLUMN output_sealed bytea;
ALTER TABLE run_targets ADD COLUMN output_key_id text;

-- A schema read is not sealed: what comes back is a table definition, nobody's data, and
-- the planner has to read it as plain text. The distinction is in `purpose`, not here.

--rollback ALTER TABLE run_targets DROP COLUMN IF EXISTS output_key_id;
--rollback ALTER TABLE run_targets DROP COLUMN IF EXISTS output_sealed;
