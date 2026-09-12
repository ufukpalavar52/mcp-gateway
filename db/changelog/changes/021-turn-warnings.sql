--liquibase formatted sql

--changeset mcp-panel:021-turn_warnings dbms:postgresql
--comment: turun uyarilari

-- Uydurma filtre uyarisi canli konsolda gorunuyor, tura yazilmiyordu: eski bir sohbeti
-- acinca ifade duruyor, onun neyi disarida biraktigi durmuyordu. Uyari tam da sonradan
-- okundugunda ise yariyor -- "493" sayisinin her seyi mi saydigini, o an bakan degil, bir
-- hafta sonra bakan sorar.
--
-- jsonb, cunku birden fazla olabiliyor ve her biri ayri bir cumle; metne birlestirip geri
-- ayristirmak, icinde ayrac gecen ilk uyaride bozulurdu.
ALTER TABLE conversation_turns ADD COLUMN warnings jsonb;

--rollback ALTER TABLE conversation_turns DROP COLUMN IF EXISTS warnings;
