--liquibase formatted sql

--changeset mcp-panel:027-turn_statements dbms:postgresql
--comment: bir turda onaya sunulan butun komutlar

-- Bir tur tek komut ve tek aksiyon saklardi. "Betigi yaz ve calistir" gibi, butun
-- komutlari tek cumleden cozulen bir plan ise ikiye bolunuyordu: iki kart, iki onay, iki
-- model cagrisi ve iki bekleyis -- verilmis tek bir karar icin.
--
-- statement ve action_id oldugu gibi kaliyor, ilkini tutuyorlar. statements bos olan her
-- eski tur bugunku yolu izler; yeni sutunlar eklenir, hicbir satir yeniden yazilmaz.
--
-- Toplu onay her plan icin degil: bir aksiyon onceki bir cevabi bekliyorsa komutu
-- planlama aninda henuz yoktur, ekrana konacak bir sey yoktur, ve gormedigini onaylamak
-- tam olarak bu yolun engellemek icin var oldugu sey.

alter table conversation_turns add column if not exists statements jsonb;
alter table conversation_turns add column if not exists action_ids jsonb;

--rollback alter table conversation_turns drop column if exists statements;
--rollback alter table conversation_turns drop column if exists action_ids;
