--liquibase formatted sql

--changeset mcp-panel:015-run_references dbms:postgresql
--comment: runs tablosuna executor'in UUID referanslari

-- mcp-action bir isi kendi urettigi UUID'lerle raporlar: is basina run_ref, aksiyon
-- basina action_ref. Bu kolonlar olmadan gelen sonucu hangi satira yazacagimizi
-- bilemeyiz -- runs.id bir bigint identity ve executor onu hic gormuyor.
--
-- Nullable, cunku bu tablo kuyruk devreye girmeden once de kullaniliyordu; eski
-- satirlarin referansi yok ve olmasi da gerekmiyor.
ALTER TABLE runs ADD COLUMN run_ref    text;
ALTER TABLE runs ADD COLUMN action_ref text;

-- Sonuc geldiginde tek satir aranir, o yuzden benzersiz. Kismi indeks: referanssiz
-- eski satirlarin hepsi NULL ve bir digerini engellememeli.
CREATE UNIQUE INDEX runs_action_ref_unique ON runs (action_ref) WHERE action_ref IS NOT NULL;
CREATE INDEX runs_run_ref_idx ON runs (run_ref) WHERE run_ref IS NOT NULL;

--rollback DROP INDEX IF EXISTS runs_run_ref_idx;
--rollback DROP INDEX IF EXISTS runs_action_ref_unique;
--rollback ALTER TABLE runs DROP COLUMN IF EXISTS action_ref;
--rollback ALTER TABLE runs DROP COLUMN IF EXISTS run_ref;
