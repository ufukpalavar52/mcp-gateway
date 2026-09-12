--liquibase formatted sql

--changeset mcp-panel:024-turn_deferred dbms:postgresql
--comment: turun ertelenen aksiyonlari

-- Planin bildigi bir sey turda kayboluyordu. "Ismi Mehmet olan kullaniciyi bul ve sil"
-- istegi iki aksiyon seciyor ve ikincisi calisamiyor: sildigi id, birincinin cevabinda ve
-- plan aninda henuz yok. Planlayici bunu "DELETE ertelendi, id bekliyor" diye kaydediyor,
-- gateway ise tura yalnizca calisan adimi yaziyordu.
--
-- Sonucu su oluyordu: is bitip goal loop devreye girdiginde elinde yalnizca calisan adim
-- kaliyor ve "devam edeyim mi" diye modele soruyordu. Model iki ayri denemede "bitti"
-- dedi -- gerekcesi "simdi bu kullanici silinmeli" derken. Cevabi bir tahmin olarak
-- sormak yerine kayitli bir olgu olarak okumak icin bu kolon var.
--
-- jsonb, cunku birden fazla aksiyon ertelenebilir ve her biri kendi bekledigi girdileri
-- tasir: {"actionId": 64, "name": "...", "waitingOn": ["id"]}.
ALTER TABLE conversation_turns ADD COLUMN deferred jsonb;

--rollback ALTER TABLE conversation_turns DROP COLUMN IF EXISTS deferred;
