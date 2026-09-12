--liquibase formatted sql

--changeset mcp-panel:023-warning_shape dbms:postgresql
--comment: uyarilarin eski dizi bicimini {code, detail} nesnesine cevirir

-- Uyarilar once bir cumle dizisiydi, sonra {code, detail} nesnesi oldu. Kolonun tipi
-- degismedi -- ikisi de jsonb -- ama icindeki elemanin sekli degisti, ve eski satirlar
-- oldugu gibi kaldi. Hibernate onlari List<Map> olarak okuyamayinca o satiri iceren her
-- sayfa 500 dondu: size=5 calisiyor, size=50 calismiyordu.
--
-- Bir jsonb kolonunun eleman seklini degistirmek, adi disinda her yonuyle sema
-- degisikligi. Kod degisikligi gibi ele alindiginda arkasinda okunamayan satirlar birakir.
UPDATE conversation_turns
   SET warnings = (
       SELECT jsonb_agg(jsonb_build_object(
           'code', 'unrequested_filter',
           -- Ilk bicim tam bir cumleydi: "The query filters on X — nothing in the
           -- request asked for that". Filtrenin kendisi cikarilir; cumleyi detail
           -- olarak birakmak, panelin kendi cumlesinin icine ikinci bir cumle koyardi.
           'detail', trim(
               regexp_replace(
                   regexp_replace(entry, '^The query filters on ', ''),
                   ' — nothing in the request asked for that$', ''))))
       FROM jsonb_array_elements_text(warnings) AS entry)
 WHERE warnings IS NOT NULL
   AND jsonb_typeof(warnings) = 'array'
   AND jsonb_array_length(warnings) > 0
   AND jsonb_typeof(warnings -> 0) = 'string';

--rollback -- Geri donus yok: eski bicim bilgi kaybi olmadan geri uretilemez ve okunamiyordu.
