--liquibase formatted sql

--changeset mcp-panel:022-goal_steps dbms:postgresql
--comment: bir hedefin adimlari

-- Bir istem birden fazla sorgu gerektirebiliyor ve yalnizca birini calistiriyordu: "ismi
-- ali ve veli olanlari getir iki ayri tablo olarak" yalnizca ali'yi getirdi, digeri ayri
-- bir soru olarak tekrar yazilmak zorunda kaldi.
--
-- Adim ayri bir tablo degil, sohbetin bir turu: kullanicinin gordugu sey zaten ardisik iki
-- tur ve ikisini ayri bir yapiya koymak, ekranda ayni sekilde cizilen iki farkli sey
-- olustururdu. goal_turn_id, bir turun hangi hedefin devami oldugunu soyler; bos ise o tur
-- hedefin kendisidir.
ALTER TABLE conversation_turns ADD COLUMN goal_turn_id bigint
    REFERENCES conversation_turns(id) ON DELETE CASCADE;

CREATE INDEX conversation_turns_goal_idx ON conversation_turns (goal_turn_id)
    WHERE goal_turn_id IS NOT NULL;

--rollback DROP INDEX IF EXISTS conversation_turns_goal_idx;
--rollback ALTER TABLE conversation_turns DROP COLUMN IF EXISTS goal_turn_id;
