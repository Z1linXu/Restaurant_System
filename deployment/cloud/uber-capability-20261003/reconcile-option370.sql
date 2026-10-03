-- Only inside apply-once.py's Store-locked, history-protected transaction.
DO $$ BEGIN
 IF current_database()<>'restaurant_pos_staging' THEN RAISE EXCEPTION 'WRONG_DATABASE'; END IF;
 IF (SELECT count(*) FROM stores WHERE id=1 AND organization_id=1 AND code='STG005_SRC_20260809_R01')<>1
 THEN RAISE EXCEPTION 'STORE_DRIFT'; END IF;
 IF (SELECT count(*) FROM menu_item_options o JOIN menu_items i ON i.id=o.menu_item_id
 WHERE o.id=370 AND i.id=25 AND i.store_id=1 AND i.sku='braised_beef_noodle'
 AND o.option_code IS NULL AND o.option_group IS NULL AND o.option_type='addon'
 AND o.name_zh='加蛋' AND o.name_en='Extra Egg' AND o.price_delta=1.99 AND o.is_active
 AND o.store_addon_id IS NULL AND o.store_addon_store_id IS NULL AND o.addon_eligible IS NULL
 AND o.parent_option_id IS NULL)<>1 THEN RAISE EXCEPTION 'OPTION370_BASELINE_DRIFT'; END IF;
 IF EXISTS(SELECT 1 FROM menu_item_options WHERE parent_option_id=370 OR (menu_item_id=25 AND id<>370 AND option_code='tea_egg'))
 THEN RAISE EXCEPTION 'OPTION370_RELATION_DRIFT'; END IF;
 IF (SELECT count(*) FROM store_addons c JOIN organization_addon_definitions d ON d.id=c.organization_addon_definition_id
 WHERE c.id=60 AND c.store_id=1 AND c.organization_id=1 AND d.organization_id=1 AND d.code='tea_egg'
 AND c.name_zh='加卤蛋' AND c.name_en='Extra Tea Egg' AND c.price=1.99 AND c.active)<>1
 THEN RAISE EXCEPTION 'CANONICAL_TEA_EGG_DRIFT'; END IF;
END $$;
CREATE TEMP TABLE options_before ON COMMIT DROP AS SELECT * FROM menu_item_options;
UPDATE menu_item_options SET option_code='tea_egg',option_group='ADD_ON',option_type='addon',
 name_zh='加卤蛋',name_en='Extra Tea Egg',store_addon_id=60,store_addon_store_id=1,
 addon_eligible=true,updated_at=localtimestamp WHERE id=370;
UPDATE stores SET menu_revision=menu_revision+1,menu_updated_at=localtimestamp,updated_at=localtimestamp WHERE id=1;
DO $$ BEGIN
 IF (SELECT count(*) FROM menu_item_options WHERE id=370 AND menu_item_id=25 AND option_code='tea_egg'
 AND option_group='ADD_ON' AND option_type='addon' AND name_zh='加卤蛋' AND name_en='Extra Tea Egg'
 AND price_delta=1.99 AND is_active AND store_addon_id=60 AND store_addon_store_id=1 AND addon_eligible)<>1
 THEN RAISE EXCEPTION 'OPTION370_READBACK_FAILED'; END IF;
 IF EXISTS(SELECT * FROM options_before WHERE id<>370 EXCEPT SELECT * FROM menu_item_options WHERE id<>370)
 OR EXISTS(SELECT * FROM menu_item_options WHERE id<>370 EXCEPT SELECT * FROM options_before WHERE id<>370)
 THEN RAISE EXCEPTION 'UNRELATED_OPTION_CHANGED'; END IF;
 IF EXISTS(SELECT 1 FROM options_before b JOIN menu_item_options a USING(id) WHERE b.id=370 AND
 (to_jsonb(a)-ARRAY['option_code','option_group','option_type','name_zh','name_en','store_addon_id','store_addon_store_id','addon_eligible','updated_at'])
 IS DISTINCT FROM (to_jsonb(b)-ARRAY['option_code','option_group','option_type','name_zh','name_en','store_addon_id','store_addon_store_id','addon_eligible','updated_at']))
 THEN RAISE EXCEPTION 'PRESERVED_OPTION_FIELD_CHANGED'; END IF;
END $$;
INSERT INTO audit_logs(store_id,action,entity_type,entity_id,summary,metadata_json,created_at)
VALUES(1,'OWNER_CONFIRMED_ADDON_IDENTITY','MENU_ITEM_OPTION',370,'Owner-confirmed tea_egg identity; ID/price/history preserved',
 '{"human_confirmed":true,"code":"tea_egg","option_id":370,"scope":"staging_store_1"}',localtimestamp);
