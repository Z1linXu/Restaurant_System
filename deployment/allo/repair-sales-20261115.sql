-- Owner attachment 461d7ddd authorizes these two bounded Allô-only changes.
-- Run after V44, with a verified recovery backup, while backend is stopped.
-- No dispatch/outbox/job/task/inventory snapshot is rewritten or replayed.
BEGIN;
SET LOCAL TIME ZONE 'America/Toronto';
LOCK TABLE orders IN SHARE ROW EXCLUSIVE MODE;
DO $$ BEGIN
 IF (SELECT count(*) FROM stores)<>1 OR NOT EXISTS(SELECT 1 FROM stores WHERE id=1 AND organization_id=1 AND code='ALLO_NOUILLES_TEST' AND timezone='America/Toronto')
 THEN RAISE EXCEPTION 'Allo singleton identity mismatch'; END IF;
 IF (SELECT max(version::int) FROM flyway_schema_history WHERE success AND version IS NOT NULL)<>44
 THEN RAISE EXCEPTION 'V44 required'; END IF;
 IF EXISTS(SELECT 1 FROM orders WHERE store_id=1 AND order_type IN ('pickup','takeout') AND status IN ('submitted','preparing','ready','picked_up')
 AND financial_mode='IN_STORE' AND external_source IS NULL AND submitted_at IS NOT NULL
 AND (subtotal_amount<0 OR discount_amount<0 OR total_amount<0 OR total_amount<subtotal_amount-discount_amount))
 THEN RAISE EXCEPTION 'Takeout money validation failed'; END IF;
END $$;
CREATE TEMP TABLE repair_targets ON COMMIT DROP AS
 SELECT o.*,o.submitted_at::date AS business_date FROM orders o
 WHERE store_id=1 AND order_type IN ('pickup','takeout') AND status IN ('submitted','preparing','ready','picked_up')
 AND financial_mode='IN_STORE' AND external_source IS NULL AND submitted_at IS NOT NULL
 AND NOT EXISTS(SELECT 1 FROM allo_order_adjustments a WHERE a.order_id=o.id AND a.request_key='takeout-repair-20261115-'||o.id);
INSERT INTO allo_order_adjustments(store_id,order_id,business_date,request_key,request_hash,actor_id,actor_name,action,reason,before_snapshot,after_snapshot,item_changes,collected_delta,created_at)
 SELECT store_id,id,business_date,'takeout-repair-20261115-'||id,encode(sha256(convert_to('Allo Takeout repair 20261115 / order '||id,'UTF8')),'hex'),NULL,
 'SYSTEM / OWNER_AUTHORIZED_20261115','TAKEOUT_REPAIR','Original submitted business date retained; no print or inventory replay',
 json_build_object('gross',subtotal_amount,'discount',discount_amount,'net',subtotal_amount-discount_amount,'tax',coalesce(tax_amount,total_amount-(subtotal_amount-discount_amount)),'total',total_amount,'collected',0)::text,
 json_build_object('gross',subtotal_amount,'discount',discount_amount,'net',subtotal_amount-discount_amount,'tax',coalesce(tax_amount,total_amount-(subtotal_amount-discount_amount)),'total',total_amount,'collected',total_amount)::text,
 '[]',total_amount,localtimestamp FROM repair_targets;
UPDATE orders o SET status='completed',completed_at=t.submitted_at,
 tax_amount=coalesce(o.tax_amount,o.total_amount-(o.subtotal_amount-o.discount_amount)),
 current_revision=coalesce(o.current_revision,0)+1,updated_at=localtimestamp
 FROM repair_targets t WHERE o.id=t.id;
SELECT json_build_object('takeout_repaired',coalesce(sum(orders),0),'dates',coalesce(json_agg(d),'[]')) FROM
 (SELECT business_date,count(*) AS orders,sum(total_amount) AS collected_delta FROM repair_targets GROUP BY business_date ORDER BY business_date)d;
-- Exact B1-B8 Store-owned group and option identities; no fuzzy-name matching or hard delete.
CREATE TEMP TABLE retired_spice ON COMMIT DROP AS
 SELECT g.id,g.menu_item_id,g.group_code FROM template_item_option_groups g
 JOIN menu_items i ON i.id=g.menu_item_id
 WHERE g.store_id=1 AND i.store_id=1 AND i.sku IN ('AN_B1','AN_B2','AN_B3','AN_B4','AN_B5','AN_B6','AN_B7','AN_B8')
 AND g.group_code='allo_spicy_'||lower(i.sku)||'_v1' AND g.kind='SPICY_LEVEL' AND (g.is_active OR g.required);
UPDATE template_item_option_groups g SET is_active=false,required=false,min_select=0
 FROM retired_spice t WHERE g.id=t.id;
UPDATE menu_item_options o SET is_active=false,template_default=false,updated_at=localtimestamp
 FROM retired_spice t WHERE o.menu_item_id=t.menu_item_id AND o.template_group_code=t.group_code AND o.option_group='SPICY_LEVEL';
UPDATE stores SET menu_revision=menu_revision+1,menu_updated_at=localtimestamp,updated_at=localtimestamp
 WHERE id=1 AND EXISTS(SELECT 1 FROM retired_spice);
SELECT json_build_object('spice_groups_retired',count(*)) FROM retired_spice;
COMMIT;
