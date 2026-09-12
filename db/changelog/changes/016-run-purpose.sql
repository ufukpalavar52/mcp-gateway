--liquibase formatted sql

--changeset mcp-panel:016-run_purpose dbms:postgresql
--comment: runs tablosuna calistirmanin amaci

-- Bir calistirma iki sebeple olabilir: kullanicinin istedigi is, ya da bu servisin kendi
-- sordugu bir sey (su an yalnizca sema okuma). Sonuc ayni kuyruktan geliyor ve ayni sekilde
-- goruniyor; hangisi oldugunu soyleyen bir sey olmadan sema cevabini nereye yazacagimizi
-- bilemeyiz.
--
-- text, enum degil: bu liste buyuyecek ve her yeni deger icin migration istemesi
-- kazandirdigindan fazlasina mal olur.
ALTER TABLE runs ADD COLUMN purpose text NOT NULL DEFAULT 'execute';

--rollback ALTER TABLE runs DROP COLUMN IF EXISTS purpose;
