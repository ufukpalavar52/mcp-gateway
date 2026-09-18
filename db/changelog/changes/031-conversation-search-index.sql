--liquibase formatted sql

--changeset mcp-panel:031-conversation_search_index dbms:postgresql
--comment: sohbet aramasini taramadan kurtaran trigram indeksleri

-- ConversationRepository.search() basta joker iceren bir LIKE kullaniyor:
--
--     lower(t.prompt) like '%...%'
--
-- Boyle bir deseni hicbir B-tree indeksi karsilayamaz. conversation_turns uzerinde uc
-- indeks var ama bu sorgu onlarin hicbirine dokunmuyor; her aramada tablonun tamami
-- okunuyor. Ustelik sorgu `distinct` ve sayfali oldugu icin tarama bir de sayim icin
-- tekrarlaniyor.
--
-- Bugun 58 satirda bunun maliyeti sifir. Yuz bin tur civarinda arama kutusu hissedilir
-- sekilde yavaslar; gunde bin tur yazan bir kurulumda bu birkac ay demek. Indeksi o gun
-- degil bugun eklemek, yavasligi hic gormemek anlamina geliyor.
--
-- Uc ayrinti, hepsi sonradan kafa karistiran cinsten:
--
-- 1. Indeksler `lower(sutun)` IFADESI uzerinde, ciplak sutun uzerinde degil. Sorgu
--    lower() ile sariyor ve ciplak sutuna kurulan bir indeks kullanilmazdi -- sessizce
--    yanlis yapilabilecek yer burasi, cunku indeks olusur, hata vermez ve hicbir ise
--    yaramaz.
--
-- 2. Trigram, adi ustunde, uc harflik parcalar uzerinden calisir. Iki karakterlik bir
--    arama yine taramaya duser. Eksiklik degil, sinirin nerede oldugunun kaydi.
--
-- 3. Uygulandiktan hemen sonra EXPLAIN yine Seq Scan gosterir ve indeks calismiyor
--    sanilir. Calisiyor: 58 satirda sirali tarama gercekten daha hizli ve planlayici
--    dogru karari veriyor. Kullanilabilir oldugunu gormek icin `SET enable_seqscan = off`
--    ile zorlamak gerekir. Indeks kendini ancak hacimle gosterir.
--
-- pg_trgm imajda zaten mevcut (surum 1.6) ve PG13'ten beri "trusted", yani veritabani
-- sahibi kurabiliyor; superuser gerekmiyor.

CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE INDEX conversations_title_trgm
    ON conversations USING gin (lower(title) gin_trgm_ops);

CREATE INDEX conversation_turns_prompt_trgm
    ON conversation_turns USING gin (lower(prompt) gin_trgm_ops);

CREATE INDEX conversation_turns_statement_trgm
    ON conversation_turns USING gin (lower(statement) gin_trgm_ops);

--rollback DROP INDEX IF EXISTS conversation_turns_statement_trgm;
--rollback DROP INDEX IF EXISTS conversation_turns_prompt_trgm;
--rollback DROP INDEX IF EXISTS conversations_title_trgm;
--rollback DROP EXTENSION IF EXISTS pg_trgm;
