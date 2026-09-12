--liquibase formatted sql

--changeset mcp-panel:017-run_target_rows dbms:postgresql
--comment: run_targets tablosuna sorgu sonucunun satirlari

-- Bir komut ekrana yazar, bir sorgu satir dondurur. Ikisi de stdout_excerpt'e metin olarak
-- konuyordu; sonuc bir tablo oldugu halde panele duz yazi olarak gidiyordu ve kullanici
-- hizalanmis bosluklara bakiyordu.
--
-- jsonb, metin degil: panelin gercek bir tablo cizebilmesi icin kolonlarin ve degerlerin
-- ayri durmasi gerekiyor. Metni geri ayristirmak, iceriginde iki bosluk gecen tek bir
-- hucrede bozulurdu.
ALTER TABLE run_targets ADD COLUMN result_rows jsonb;

--rollback ALTER TABLE run_targets DROP COLUMN IF EXISTS result_rows;
