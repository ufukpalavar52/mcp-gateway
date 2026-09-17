--liquibase formatted sql

--changeset mcp-panel:028-definition_access dbms:postgresql
--comment: tanim bazinda calistirma ve duzenleme yetkisi

-- Yetki bugune kadar yalnizca rol duzeyindeydi: DEVELOPER olan herkes yayimlanmis her
-- araci calistirabiliyordu -- allowedCommands ["*"] ve sudo tasiyani dahil.
--
-- access varsayilani OPEN, yani mevcut hicbir tanimin davranisi degismiyor. Kisitlama
-- acik bir karardir: listedeki son kisi silinse bile tanim RESTRICTED kalir ve kimse
-- calistiramaz. Aksi halde son satiri silmek tanimi herkese acardi ve ekranda hicbir sey
-- degismezdi -- goruilmeyen bir yetki kaybi.

CREATE TYPE definition_access AS ENUM ('open', 'restricted');

ALTER TABLE definitions
    ADD COLUMN access definition_access NOT NULL DEFAULT 'open';

CREATE TABLE definition_permissions (
    id            BIGSERIAL PRIMARY KEY,
    definition_id BIGINT      NOT NULL REFERENCES definitions (id) ON DELETE CASCADE,
    user_id       BIGINT      NOT NULL REFERENCES users (id)       ON DELETE CASCADE,
    can_run       BOOLEAN     NOT NULL DEFAULT TRUE,
    -- Duzenleyebilen zaten calistirabilir: komutu degistirip calistirabilecek birine
    -- "calistiramaz" demek kendini kandirmaktir. Uygulama bunu okurken varsayar.
    can_edit      BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT definition_permissions_unique UNIQUE (definition_id, user_id)
);

CREATE INDEX definition_permissions_user ON definition_permissions (user_id);

-- Bugun DEVELOPER olan herkes, bugun var olan her tanimi duzenleyebiliyordu. OPEN olmak
-- "calistirmaya acik" demek, "duzenlemeye acik" degil -- bir tanimi yeniden yazilmaktan
-- korumanin tek yolu onu calistirmaya kapatmak olsaydi, iki ayri soruya tek cevap vermis
-- olurduk. Dolayisiyla duzenleme her durumda acik bir izin ister.
--
-- Bu satir o izni gecmise donuk veriyor: goc, kimsenin elinden bugun sahip oldugu bir
-- yetkiyi almadan gecsin. Bir kez calisir; bundan sonra acilan tanimlar ve sonradan
-- eklenen kullanicilar izinlerini acikca alir.
--
-- ADMIN'lere satir yazilmiyor: onlar zaten her tanima erisiyor.
INSERT INTO definition_permissions (definition_id, user_id, can_run, can_edit)
SELECT d.id, u.id, TRUE, TRUE
FROM definitions d
CROSS JOIN users u
WHERE u.role = 'developer'
ON CONFLICT ON CONSTRAINT definition_permissions_unique DO NOTHING;

--rollback DROP TABLE IF EXISTS definition_permissions;
--rollback ALTER TABLE definitions DROP COLUMN IF EXISTS access;
--rollback DROP TYPE IF EXISTS definition_access;
