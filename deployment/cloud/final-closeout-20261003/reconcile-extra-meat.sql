-- Explicit Owner decision: extra_meat / 加肉 / Extra Meat; all existing prices 6.99.
-- Four missing-code rows have no competing same-item identity. Inactive duplicate
-- #265 and every egg/cabbage/broccoli ambiguity remain untouched.
CREATE TEMP TABLE approved_extra_meat(id bigint PRIMARY KEY, item_id bigint, old_code text, old_en text, active boolean) ON COMMIT DROP;
INSERT INTO approved_extra_meat VALUES
 (37,1,'extra_meat','Extra Beef',true),(154,4,'extra_meat','Extra Meat',true),
 (183,5,'extra_meat','Extra Meat',true),(212,3,'extra_meat','Extra Beef',true),
 (247,2,'extra_meat','Extra Beef',true),(308,24,'extra_meat','Extra Meat',true),
 (346,26,'extra_meat','Extra Beef',true),(65,16,NULL,'Extra Meat',false),
 (91,15,NULL,'Extra Meat',false),(117,14,NULL,'Extra Meat',false),(369,25,NULL,'Extra Meat',true);
DO $$ BEGIN
 IF (SELECT count(*) FROM approved_extra_meat a JOIN menu_item_options o ON o.id=a.id
 JOIN menu_items i ON i.id=o.menu_item_id WHERE i.store_id=1 AND o.menu_item_id=a.item_id
 AND o.option_code IS NOT DISTINCT FROM a.old_code AND o.name_zh='加肉' AND o.name_en=a.old_en
 AND o.price_delta=6.99 AND o.is_active=a.active AND o.store_addon_id IS NULL
 AND o.parent_option_id IS NULL AND o.option_type='addon')<>11
 THEN RAISE EXCEPTION 'EXTRA_MEAT_BASELINE_DRIFT'; END IF;
 IF EXISTS(SELECT 1 FROM menu_item_options o JOIN approved_extra_meat a ON a.item_id=o.menu_item_id
 WHERE o.option_code='extra_meat' AND o.id<>a.id)
 THEN RAISE EXCEPTION 'EXTRA_MEAT_DUPLICATE_IDENTITY'; END IF;
 IF EXISTS(SELECT 1 FROM store_addons a JOIN organization_addon_definitions d ON d.id=a.organization_addon_definition_id
 WHERE a.store_id=1 AND d.code='extra_meat') THEN RAISE EXCEPTION 'CATALOG_ALREADY_EXISTS'; END IF;
END $$;
-- Same lock order as StoreAddonService: Store then Organization.
SELECT id FROM organizations WHERE id=1 FOR UPDATE;
INSERT INTO organization_addon_definitions(organization_id,code,created_at,updated_at)
 SELECT 1,'extra_meat',localtimestamp,localtimestamp
 WHERE NOT EXISTS(SELECT 1 FROM organization_addon_definitions WHERE organization_id=1 AND code='extra_meat');
INSERT INTO store_addons(store_id,organization_id,organization_addon_definition_id,name_zh,name_en,price,active,created_at,updated_at)
 SELECT 1,1,id,'加肉','Extra Meat',6.99,true,localtimestamp,localtimestamp
 FROM organization_addon_definitions WHERE organization_id=1 AND code='extra_meat';
UPDATE menu_item_options o SET option_code='extra_meat',option_group='ADD_ON',name_en='Extra Meat',
 store_addon_id=c.id,store_addon_store_id=1,addon_eligible=o.is_active,updated_at=localtimestamp
 FROM approved_extra_meat p,store_addons c,organization_addon_definitions d
 WHERE o.id=p.id AND c.store_id=1 AND d.id=c.organization_addon_definition_id AND d.code='extra_meat';
UPDATE stores SET menu_revision=menu_revision+1,menu_updated_at=localtimestamp,updated_at=localtimestamp WHERE id=1;
DO $$ BEGIN
 IF (SELECT count(*) FROM approved_extra_meat p JOIN menu_item_options o ON o.id=p.id
 JOIN store_addons c ON c.id=o.store_addon_id WHERE o.option_code='extra_meat' AND o.name_zh='加肉'
 AND o.name_en='Extra Meat' AND o.price_delta=6.99 AND o.is_active=p.active AND o.addon_eligible=p.active
 AND c.store_id=1 AND c.price=6.99 AND c.active)<>11 THEN RAISE EXCEPTION 'EXTRA_MEAT_READBACK_FAILED'; END IF;
END $$;
