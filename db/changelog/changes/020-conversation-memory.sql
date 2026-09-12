--liquibase formatted sql

--changeset mcp-panel:020-conversation-memory dbms:postgresql
--comment: uzun sohbetlerin özeti ve araçsız cevaplar

-- Bir soru, kendinden önce sorulanlara dayanabiliyor artık; ama pencere kayar. Elli turdan
-- sonra sohbetin başı — herkesin ne hakkında konuştuğunu belirleyen soru — pencerenin
-- dışında kalıyor ve sanki hiç sorulmamış gibi oluyordu.
--
-- Özet, düşen turlar üzerine katlanarak büyür: her seferinde bütün sohbeti yeniden
-- okumak, bininci soruda ellinci sorudakinin yirmi katı maliyet demekti. summarised_through_id
-- nereye kadar katlandığını tutar, böylece aynı tur iki kez özete girmez.
ALTER TABLE conversations ADD COLUMN summary               text;
ALTER TABLE conversations ADD COLUMN summarised_through_id bigint;

-- Konsola yazılan her şey bir veri sorusu değil: "az önce ne çalıştırdım", "bu sorgu ne
-- yapıyor", "neden boş döndü" — hiçbiri bir araç çağırmayı gerektirmiyor ve hepsi
-- sohbetin kendisinden cevaplanabiliyor. Bunlara "eşleşen araç yok" demek, konsolu tam da
-- insanların ona sorduğu sorular için işe yaramaz kılıyordu.
--
-- statement'tan ayrı kolon, çünkü ayrı şey: biri bir sistemin çalıştırdığı, diğeri bir
-- modelin söylediği. Aynı yerde tutmak, hesap sayısı tahminini sayımdan ayırt edilemez
-- hale getirirdi.
ALTER TABLE conversation_turns ADD COLUMN answer text;

--rollback ALTER TABLE conversation_turns DROP COLUMN IF EXISTS answer;
--rollback ALTER TABLE conversations DROP COLUMN IF EXISTS summarised_through_id;
--rollback ALTER TABLE conversations DROP COLUMN IF EXISTS summary;
