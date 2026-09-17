--liquibase formatted sql

--changeset mcp-panel:029-must_change_password dbms:postgresql
--comment: yoneticinin belirledigi sifre, kullanicinin sifresi degildir

-- Bir yonetici kullanici eklerken sifreyi kendisi belirleyebilir. O sifreyi iki kisi
-- bilir, ve ikisinden biri hesabin sahibi degildir -- aradaki sure ne kadar kisa olursa o
-- kadar iyi. Bu bayrak aciksa panel sifre ekranina kilitlenir: menu yok, baska rotaya
-- gecis yok.
--
-- Varsayilan false: mevcut hicbir kullanici bir degisiklik hissetmez.

ALTER TABLE users
    ADD COLUMN must_change_password BOOLEAN NOT NULL DEFAULT FALSE;

--rollback ALTER TABLE users DROP COLUMN IF EXISTS must_change_password;
