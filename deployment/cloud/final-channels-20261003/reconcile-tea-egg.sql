-- Exact Owner-confirmed tea_egg identities from the Staging audit. No name matching.
-- Called only inside apply-menu-once.py's Store-locked SERIALIZABLE transaction.
-- Preserve IDs, prices, active state, every other option and all historical snapshots.
CREATE TEMP TABLE approved_tea_egg(id bigint PRIMARY KEY,item_id bigint,sku text,old_zh text) ON COMMIT DROP;
INSERT INTO approved_tea_egg VALUES
 (36,1,'traditional_beef_noodle','加蛋'),
 (50,17,'vegetable_chow_mein','加卤蛋'),
 (76,16,'tomato_chow_mein','加卤蛋'),
 (102,15,'chicken_chow_mein','加卤蛋'),
 (128,14,'beef_chow_mein','加卤蛋'),
 (155,4,'dan_dan_noodle','加蛋'),
 (184,5,'zha_jiang_noodle','加蛋'),
 (213,3,'vegetable_noodle','加蛋'),
 (248,2,'braised_beef_tendon_noodle','加蛋'),
 (309,24,'cold_noodle_shredded_chicken','加蛋'),
 (347,26,'pickled_vegetable_beef_noodle','加蛋');
DO $$ BEGIN
 IF (SELECT count(*) FROM approved_tea_egg p JOIN menu_item_options o ON o.id=p.id
 JOIN menu_items i ON i.id=o.menu_item_id WHERE i.store_id=1 AND i.id=p.item_id AND i.sku=p.sku
 AND o.option_code='tea_egg' AND o.option_group='ADD_ON' AND o.option_type='addon'
 AND o.name_zh=p.old_zh AND o.name_en='Extra Tea Egg' AND o.price_delta=1.99
 AND o.is_active=true AND o.store_addon_id IS NULL AND o.store_addon_store_id IS NULL
 AND o.addon_eligible IS NULL AND o.parent_option_id IS NULL)<>11
 THEN RAISE EXCEPTION 'TEA_EGG_BASELINE_DRIFT'; END IF;
 IF (SELECT count(*) FROM menu_item_options o JOIN menu_items i ON i.id=o.menu_item_id
 WHERE i.store_id=1 AND o.option_code='tea_egg')<>11 THEN RAISE EXCEPTION 'TEA_EGG_IDENTITY_DRIFT'; END IF;
 IF EXISTS(SELECT 1 FROM store_addons a JOIN organization_addon_definitions d ON d.id=a.organization_addon_definition_id
 WHERE a.store_id=1 AND d.code='tea_egg') THEN RAISE EXCEPTION 'TEA_EGG_CATALOG_ALREADY_EXISTS'; END IF;
END $$;
-- Same Store -> Organization lock order as StoreAddonService.
SELECT id FROM organizations WHERE id=1 FOR UPDATE;
INSERT INTO organization_addon_definitions(organization_id,code,created_at,updated_at)
 SELECT 1,'tea_egg',localtimestamp,localtimestamp
 WHERE NOT EXISTS(SELECT 1 FROM organization_addon_definitions WHERE organization_id=1 AND code='tea_egg');
INSERT INTO store_addons(store_id,organization_id,organization_addon_definition_id,name_zh,name_en,price,active,created_at,updated_at)
 SELECT 1,1,id,'加卤蛋','Extra Tea Egg',1.99,true,localtimestamp,localtimestamp
 FROM organization_addon_definitions WHERE organization_id=1 AND code='tea_egg';
UPDATE menu_item_options o SET name_zh='加卤蛋',store_addon_id=c.id,store_addon_store_id=1,
 addon_eligible=o.is_active,updated_at=localtimestamp
 FROM approved_tea_egg p,store_addons c,organization_addon_definitions d
 WHERE o.id=p.id AND c.store_id=1 AND d.id=c.organization_addon_definition_id AND d.code='tea_egg';
UPDATE stores SET menu_revision=menu_revision+1,menu_updated_at=localtimestamp,updated_at=localtimestamp WHERE id=1;
DO $$ BEGIN
 IF (SELECT count(*) FROM approved_tea_egg p JOIN menu_item_options o ON o.id=p.id
 JOIN store_addons c ON c.id=o.store_addon_id JOIN organization_addon_definitions d ON d.id=c.organization_addon_definition_id
 WHERE o.menu_item_id=p.item_id AND o.option_code='tea_egg' AND o.option_group='ADD_ON' AND o.name_zh='加卤蛋'
 AND o.name_en='Extra Tea Egg' AND o.price_delta=1.99 AND o.is_active=true AND o.addon_eligible=true
 AND o.store_addon_store_id=1 AND c.store_id=1 AND c.organization_id=1 AND c.price=1.99 AND c.active=true
 AND c.name_zh='加卤蛋' AND c.name_en='Extra Tea Egg' AND d.code='tea_egg')<>11
 THEN RAISE EXCEPTION 'TEA_EGG_READBACK_FAILED'; END IF;
END $$;
