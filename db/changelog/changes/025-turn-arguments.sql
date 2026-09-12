--liquibase formatted sql

--changeset mcp-panel:025-turn_arguments dbms:postgresql
--comment: adimin planlandigi degerler

-- Onaya alinan bir adim, hangi degerlerle planlandigini unutuyordu.
--
-- Onaylamak adimi yeniden planlar, tekrar oynatmaz: saklanan komut on dakika once
-- uretilmis bir metindir ve ona guvenip calistirmak, korkuluklardan gecmeyen tek yol
-- olurdu. Ama yeniden planlamaya giden tek sey turun cumlesiydi -- "Processing the first
-- user found with first_name 'Yigit'" -- ve o cumlede id yok. Yonlendirici bir id
-- uyduruyor, uydurdugu 59 olmadigi icin saklanan komutla eslesmiyor, expect korumasi
-- gonderimi durduruyordu. Yanlis bir sey calismiyor; kart sessizce onay bekler haline
-- geri dusuyordu ve kullanici "approve'a basiyorum, eski haline donuyor" diyordu.
--
-- Dogru cevap: degeri yeniden turetmek degil, hatirlamak. Dongunun cevaptan okudugu deger
-- burada durur ve onaylandiginda ayni degerlerle yeniden planlanir -- ayni korkuluklardan
-- gecerek, ayni komuta varmak icin.
--
-- jsonb, cunku bir adim birden fazla girdi tasiyabilir: {"id": "59", "last_name": "Polat"}.
ALTER TABLE conversation_turns ADD COLUMN arguments jsonb;

--rollback ALTER TABLE conversation_turns DROP COLUMN IF EXISTS arguments;
