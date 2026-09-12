--liquibase formatted sql

--changeset mcp-panel:019-run_target_output_sealed dbms:postgresql
--comment: çalıştırma çıktısının mühürlenerek saklanması

-- 017 ile eklenen result_rows, sorgunun döndürdüğü satırları olduğu gibi yazıyordu ve
-- stdout_excerpt aynı satırları hizalanmış metin olarak tutuyordu. Bu, kontrol düzleminin
-- veritabanını sorguladığı üretim verisinin kopyasına çevirdi: PIN, tckn, email ve ad
-- alanları burada açık metin olarak birikti.
--
-- Çıktı artık mcp-cipher ile mühürlenip tek bir zarf olarak yazılıyor; anahtar bu
-- veritabanının dışında. Metin ve satırlar aynı zarfın içinde, çünkü ikisi aynı veri ve
-- ayrı mühürlemek iki anahtar açma çağrısı demekti.
ALTER TABLE run_targets ADD COLUMN output_sealed bytea;
ALTER TABLE run_targets ADD COLUMN output_key_id text;

-- Şema okuma mühürlenmez: dönen şey tablo tanımı, kişisel veri değil, ve planlayıcı onu
-- açık metin olarak okumak zorunda. Ayrım purpose kolonunda, bu tabloda değil.

--rollback ALTER TABLE run_targets DROP COLUMN IF EXISTS output_key_id;
--rollback ALTER TABLE run_targets DROP COLUMN IF EXISTS output_sealed;
