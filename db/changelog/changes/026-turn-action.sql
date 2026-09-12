--liquibase formatted sql

--changeset mcp-panel:026-turn_action dbms:postgresql
--comment: adimin planlandigi aksiyon

-- 025 ile birlikte tek bir isin ikinci yarisi: onaya alinan adim, hangi degerlerle
-- planlandigini oldugu kadar hangi aksiyon oldugunu da unutuyordu.
--
-- Bir tanim birden fazla aksiyon tasir -- listele, olustur, guncelle, sil -- ve hangisinin
-- calisacagi her planlamada yeniden secilir. Onaylamak yeniden planlar, ve secim yeniden
-- kosunca ayni cumleden bu kez listeleme aksiyonu secildi: ekranda DELETE yaziyordu,
-- yeniden planlanan GET oldu, expect korumasi gonderimi reddetti. Kart yine onay bekler
-- haline dondu.
--
-- Onaylanan sey belirli bir komuttur, "bu cumleye uyan herhangi bir aksiyon" degil. Secim
-- bir kez yapilir ve gosterilir; onay o secime verilir.
ALTER TABLE conversation_turns ADD COLUMN action_id bigint;

--rollback ALTER TABLE conversation_turns DROP COLUMN IF EXISTS action_id;
