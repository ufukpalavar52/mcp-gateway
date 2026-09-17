--liquibase formatted sql

--changeset mcp-panel:030-password_resets dbms:postgresql
--comment: sifremi unuttum akisi

-- Giris ekranindaki "sifremi unuttum" bagi bugune kadar /login'e gidiyordu; yani hicbir
-- yere. Kayit kapali oldugu icin sifresini unutan birinin tek caresi yoneticiye gitmekti.
--
-- Token yalnizca ozetiyle saklanir, davetlerde oldugu gibi: bir satir hesabi acmaya yeter,
-- ve sizan bir veritabani linkleri de vermemeli.
--
-- Bir saat, davetteki yedi gun degil. Bir davet birini ise alma hizinda calisir; sifirlama
-- linki ise bir sifredir ve postada ne kadar az beklerse o kadar iyi.

CREATE TABLE password_resets (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash TEXT        NOT NULL UNIQUE,
    expires_at TIMESTAMPTZ NOT NULL,
    used_at    TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX password_resets_user ON password_resets (user_id);

--rollback DROP TABLE IF EXISTS password_resets;
