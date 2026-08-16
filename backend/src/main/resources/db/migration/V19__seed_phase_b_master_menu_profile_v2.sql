-- V19__seed_phase_b_master_menu_profile_v2
-- Phase B Part 1: publish the initial Chain Master Menu from the reviewed
-- ST_DENIS_CANONICAL_PROFILE/v1 artifacts and add a Phase B-ready profile v2.
-- This migration derives from profile artifacts only. It does not read Stores,
-- orders, runtime queues, auth secrets, device bindings or printer endpoints.

WITH source_profile AS (
    SELECT
        profile.id AS profile_id,
        version.id AS profile_version_id,
        profile.profile_code,
        version.profile_version,
        version.fingerprint_sha256 AS profile_fingerprint_sha256
    FROM public.store_profiles profile
    JOIN public.store_profile_versions version
        ON version.profile_id = profile.id
    WHERE profile.profile_code = 'ST_DENIS_CANONICAL_PROFILE'
      AND version.profile_version = 'v1'
      AND version.status IN ('READY', 'REVIEWED', 'PUBLISHED')
),
source_menu AS (
    SELECT
        source_profile.*,
        artifact.content_json AS menu_content_json,
        artifact.fingerprint_sha256 AS menu_template_fingerprint_sha256
    FROM source_profile
    JOIN public.store_profile_artifacts artifact
        ON artifact.profile_version_id = source_profile.profile_version_id
    WHERE artifact.artifact_type = 'MENU_TEMPLATE'
      AND artifact.artifact_code = 'MENU_TEMPLATE'
      AND artifact.artifact_version = 'v1'
)
INSERT INTO public.chain_master_menus (
    organization_id,
    master_menu_key,
    display_name,
    description,
    status,
    provenance,
    created_at,
    updated_at
)
SELECT
    organization_row.id,
    'LANZHOU_CHAIN_MASTER_MENU',
    'Lanzhou Chain Master Menu',
    'Initial Phase B chain menu derived from reviewed ST_DENIS_CANONICAL_PROFILE/v1 artifacts.',
    'PUBLISHED',
    'ST_DENIS_CANONICAL_PROFILE/v1:MENU_TEMPLATE/v1',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
FROM public.organizations organization_row
WHERE EXISTS (SELECT 1 FROM source_menu)
ON CONFLICT (organization_id, master_menu_key) DO NOTHING;

WITH source_profile AS (
    SELECT
        profile.id AS profile_id,
        version.id AS profile_version_id,
        profile.profile_code,
        version.profile_version,
        version.fingerprint_sha256 AS profile_fingerprint_sha256
    FROM public.store_profiles profile
    JOIN public.store_profile_versions version
        ON version.profile_id = profile.id
    WHERE profile.profile_code = 'ST_DENIS_CANONICAL_PROFILE'
      AND version.profile_version = 'v1'
      AND version.status IN ('READY', 'REVIEWED', 'PUBLISHED')
),
source_menu AS (
    SELECT
        source_profile.*,
        artifact.content_json AS menu_content_json,
        artifact.fingerprint_sha256 AS menu_template_fingerprint_sha256
    FROM source_profile
    JOIN public.store_profile_artifacts artifact
        ON artifact.profile_version_id = source_profile.profile_version_id
    WHERE artifact.artifact_type = 'MENU_TEMPLATE'
      AND artifact.artifact_code = 'MENU_TEMPLATE'
      AND artifact.artifact_version = 'v1'
),
category_rows AS (
    SELECT
        category_entry.value AS category_json,
        lower(category_entry.value ->> 'code') AS master_category_key,
        category_entry.ordinal
    FROM source_menu
    CROSS JOIN LATERAL jsonb_array_elements(source_menu.menu_content_json::jsonb -> 'categories')
        WITH ORDINALITY AS category_entry(value, ordinal)
),
item_rows AS (
    SELECT item_entry.value AS item_json, item_entry.ordinal
    FROM source_menu
    CROSS JOIN LATERAL jsonb_array_elements(source_menu.menu_content_json::jsonb -> 'items')
        WITH ORDINALITY AS item_entry(value, ordinal)
),
sku_counts AS (
    SELECT item_json ->> 'sku' AS sku, count(*) AS sku_count
    FROM item_rows
    GROUP BY item_json ->> 'sku'
),
product_rows AS (
    SELECT
        item_rows.item_json,
        item_rows.ordinal,
        CASE
            WHEN sku_counts.sku_count = 1 THEN item_rows.item_json ->> 'sku'
            ELSE (item_rows.item_json ->> 'sku') || ':' || (item_rows.item_json ->> 'item_ref')
        END AS master_product_key,
        category_rows.master_category_key
    FROM item_rows
    JOIN sku_counts
        ON sku_counts.sku = item_rows.item_json ->> 'sku'
    JOIN category_rows
        ON category_rows.category_json ->> 'category_ref' = item_rows.item_json ->> 'category_ref'
),
option_base AS (
    SELECT
        option_entry.value AS option_json,
        option_entry.ordinal,
        product_rows.master_product_key,
        CASE
            WHEN nullif(option_entry.value ->> 'option_code', '') IS NOT NULL
                THEN product_rows.master_product_key || ':'
                    || (option_entry.value ->> 'option_group') || ':'
                    || (option_entry.value ->> 'option_code')
            ELSE product_rows.master_product_key || ':' || (option_entry.value ->> 'option_ref')
        END AS master_option_key
    FROM source_menu
    CROSS JOIN LATERAL jsonb_array_elements(source_menu.menu_content_json::jsonb -> 'options')
        WITH ORDINALITY AS option_entry(value, ordinal)
    JOIN product_rows
        ON product_rows.item_json ->> 'item_ref' = option_entry.value ->> 'item_ref'
),
option_rows AS (
    SELECT
        option_base.option_json,
        option_base.ordinal,
        option_base.master_product_key,
        option_base.master_option_key,
        parent_option.master_option_key AS parent_master_option_key
    FROM option_base
    LEFT JOIN option_base parent_option
        ON parent_option.option_json ->> 'option_ref' = option_base.option_json ->> 'parent_option_ref'
),
master_content AS (
    SELECT jsonb_build_object(
        'schema_version', 'CHAIN_MASTER_MENU_V1',
        'master_menu_key', 'LANZHOU_CHAIN_MASTER_MENU',
        'master_menu_version', 'v1',
        'source_profile', jsonb_build_object(
            'profile_code', source_menu.profile_code,
            'profile_version', source_menu.profile_version,
            'profile_fingerprint_sha256', source_menu.profile_fingerprint_sha256,
            'menu_template_artifact_code', 'MENU_TEMPLATE',
            'menu_template_artifact_version', 'v1',
            'menu_template_fingerprint_sha256', source_menu.menu_template_fingerprint_sha256,
            'source_reference', 'ST_DENIS_CANONICAL_PROFILE/v1:MENU_TEMPLATE/v1'
        ),
        'identity_rules', jsonb_build_object(
            'category_key', 'lower(category.code)',
            'product_key', 'sku when unique else sku:item_ref',
            'option_key', 'product_key:option_group:option_code when option_code exists else product_key:option_ref'
        ),
        'counts', jsonb_build_object(
            'categories', (SELECT count(*) FROM category_rows),
            'products', (SELECT count(*) FROM product_rows),
            'options', (SELECT count(*) FROM option_rows),
            'parent_option_relationships', (
                SELECT count(*) FROM option_rows WHERE parent_master_option_key IS NOT NULL
            )
        ),
        'categories', (
            SELECT jsonb_agg(jsonb_build_object(
                'master_category_key', category_rows.master_category_key,
                'category_ref', category_rows.category_json ->> 'category_ref',
                'code', category_rows.category_json ->> 'code',
                'name_en', category_rows.category_json ->> 'name_en',
                'name_zh', category_rows.category_json ->> 'name_zh',
                'sort_order', (category_rows.category_json ->> 'sort_order')::integer,
                'default_active', (category_rows.category_json ->> 'enabled')::boolean
            ) ORDER BY category_rows.ordinal)
            FROM category_rows
        ),
        'products', (
            SELECT jsonb_agg(jsonb_build_object(
                'master_product_key', product_rows.master_product_key,
                'item_ref', product_rows.item_json ->> 'item_ref',
                'sku', product_rows.item_json ->> 'sku',
                'master_category_key', product_rows.master_category_key,
                'station_ref', product_rows.item_json ->> 'station_ref',
                'name_en', product_rows.item_json ->> 'name_en',
                'name_zh', product_rows.item_json ->> 'name_zh',
                'item_type', product_rows.item_json ->> 'item_type',
                'base_price', product_rows.item_json ->> 'base_price',
                'cost_per_item', product_rows.item_json ->> 'cost_per_item',
                'sort_order', (product_rows.item_json ->> 'sort_order')::integer,
                'default_active', (product_rows.item_json ->> 'enabled')::boolean,
                'default_sold_out', (product_rows.item_json ->> 'sold_out')::boolean,
                'combo_allowed', (product_rows.item_json ->> 'combo_allowed')::boolean
            ) ORDER BY product_rows.ordinal)
            FROM product_rows
        ),
        'options', (
            SELECT jsonb_agg(jsonb_build_object(
                'master_option_key', option_rows.master_option_key,
                'option_ref', option_rows.option_json ->> 'option_ref',
                'master_product_key', option_rows.master_product_key,
                'parent_master_option_key', option_rows.parent_master_option_key,
                'option_type', option_rows.option_json ->> 'option_type',
                'option_group', option_rows.option_json ->> 'option_group',
                'option_code', option_rows.option_json ->> 'option_code',
                'name_en', option_rows.option_json ->> 'name_en',
                'name_zh', option_rows.option_json ->> 'name_zh',
                'price_delta', option_rows.option_json ->> 'price_delta',
                'sort_order', (option_rows.option_json ->> 'sort_order')::integer,
                'default_active', (option_rows.option_json ->> 'enabled')::boolean
            ) ORDER BY option_rows.ordinal)
            FROM option_rows
        )
    ) AS content_json
    FROM source_menu
)
INSERT INTO public.chain_master_menu_versions (
    master_menu_id,
    version_key,
    status,
    schema_version,
    content_json,
    fingerprint_sha256,
    source_profile_code,
    source_profile_version,
    source_profile_fingerprint_sha256,
    source_reference,
    created_at,
    updated_at,
    published_at
)
SELECT
    master_menu.id,
    'v1',
    'PUBLISHED',
    'CHAIN_MASTER_MENU_V1',
    master_content.content_json::text,
    'e55ad23773753ade22e3e090622c361b58268ae5b43ee354ec7eef5b78f233f7',
    source_profile.profile_code,
    source_profile.profile_version,
    source_profile.profile_fingerprint_sha256,
    'ST_DENIS_CANONICAL_PROFILE/v1:MENU_TEMPLATE/v1',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
FROM public.chain_master_menus master_menu
CROSS JOIN source_profile
CROSS JOIN master_content
WHERE master_menu.master_menu_key = 'LANZHOU_CHAIN_MASTER_MENU'
ON CONFLICT (master_menu_id, version_key) DO NOTHING;

INSERT INTO public.chain_master_menu_categories (
    master_menu_version_id,
    master_category_key,
    category_ref,
    code,
    name,
    name_en,
    name_zh,
    sort_order,
    default_active,
    created_at,
    updated_at
)
SELECT
    version.id,
    category_entry.value ->> 'master_category_key',
    category_entry.value ->> 'category_ref',
    category_entry.value ->> 'code',
    coalesce(category_entry.value ->> 'name_zh', category_entry.value ->> 'name_en'),
    category_entry.value ->> 'name_en',
    category_entry.value ->> 'name_zh',
    (category_entry.value ->> 'sort_order')::integer,
    (category_entry.value ->> 'default_active')::boolean,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
FROM public.chain_master_menu_versions version
JOIN public.chain_master_menus master_menu
    ON master_menu.id = version.master_menu_id
CROSS JOIN LATERAL jsonb_array_elements(version.content_json::jsonb -> 'categories') AS category_entry(value)
WHERE master_menu.master_menu_key = 'LANZHOU_CHAIN_MASTER_MENU'
  AND version.version_key = 'v1'
ON CONFLICT (master_menu_version_id, master_category_key) DO NOTHING;

INSERT INTO public.chain_master_menu_products (
    master_menu_version_id,
    master_product_key,
    item_ref,
    sku,
    master_category_key,
    station_ref,
    name,
    name_en,
    name_zh,
    item_type,
    base_price,
    cost_per_item,
    sort_order,
    default_active,
    default_sold_out,
    combo_allowed,
    created_at,
    updated_at
)
SELECT
    version.id,
    product_entry.value ->> 'master_product_key',
    product_entry.value ->> 'item_ref',
    product_entry.value ->> 'sku',
    product_entry.value ->> 'master_category_key',
    product_entry.value ->> 'station_ref',
    coalesce(product_entry.value ->> 'name_zh', product_entry.value ->> 'name_en'),
    product_entry.value ->> 'name_en',
    product_entry.value ->> 'name_zh',
    product_entry.value ->> 'item_type',
    NULLIF(product_entry.value ->> 'base_price', '')::numeric,
    NULLIF(product_entry.value ->> 'cost_per_item', '')::numeric,
    (product_entry.value ->> 'sort_order')::integer,
    (product_entry.value ->> 'default_active')::boolean,
    (product_entry.value ->> 'default_sold_out')::boolean,
    (product_entry.value ->> 'combo_allowed')::boolean,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
FROM public.chain_master_menu_versions version
JOIN public.chain_master_menus master_menu
    ON master_menu.id = version.master_menu_id
CROSS JOIN LATERAL jsonb_array_elements(version.content_json::jsonb -> 'products') AS product_entry(value)
WHERE master_menu.master_menu_key = 'LANZHOU_CHAIN_MASTER_MENU'
  AND version.version_key = 'v1'
ON CONFLICT (master_menu_version_id, master_product_key) DO NOTHING;

INSERT INTO public.chain_master_menu_options (
    master_menu_version_id,
    master_option_key,
    option_ref,
    master_product_key,
    parent_master_option_key,
    option_type,
    option_group,
    code,
    name,
    name_en,
    name_zh,
    price_delta,
    sort_order,
    default_active,
    created_at,
    updated_at
)
SELECT
    version.id,
    option_entry.value ->> 'master_option_key',
    option_entry.value ->> 'option_ref',
    option_entry.value ->> 'master_product_key',
    option_entry.value ->> 'parent_master_option_key',
    option_entry.value ->> 'option_type',
    option_entry.value ->> 'option_group',
    option_entry.value ->> 'option_code',
    coalesce(option_entry.value ->> 'name_zh', option_entry.value ->> 'name_en'),
    option_entry.value ->> 'name_en',
    option_entry.value ->> 'name_zh',
    coalesce(NULLIF(option_entry.value ->> 'price_delta', '')::numeric, 0),
    (option_entry.value ->> 'sort_order')::integer,
    (option_entry.value ->> 'default_active')::boolean,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
FROM public.chain_master_menu_versions version
JOIN public.chain_master_menus master_menu
    ON master_menu.id = version.master_menu_id
CROSS JOIN LATERAL jsonb_array_elements(version.content_json::jsonb -> 'options') AS option_entry(value)
WHERE master_menu.master_menu_key = 'LANZHOU_CHAIN_MASTER_MENU'
  AND version.version_key = 'v1'
ON CONFLICT (master_menu_version_id, master_option_key) DO NOTHING;

WITH source_version AS (
    SELECT version.*
    FROM public.store_profiles profile
    JOIN public.store_profile_versions version
        ON version.profile_id = profile.id
    WHERE profile.profile_code = 'ST_DENIS_CANONICAL_PROFILE'
      AND version.profile_version = 'v1'
      AND version.status IN ('READY', 'REVIEWED', 'PUBLISHED')
),
profile_v2_content AS (
    SELECT
        source_version.profile_id,
        jsonb_set(
            jsonb_set(
                jsonb_set(
                    source_version.content_json::jsonb,
                    '{profile_version}',
                    to_jsonb('v2'::text),
                    false
                ),
                '{template_references,printing_display_rules}',
                jsonb_build_object(
                    'artifact_code', 'PRINTING_DISPLAY_RULES',
                    'artifact_version', 'v1',
                    'fingerprint_sha256', '442214c92fabc31801d5e9aff9e08b97eadd404b91141ad9efe28180fea081a0'
                ),
                true
            ),
            '{master_menu_reference}',
            jsonb_build_object(
                'master_menu_key', 'LANZHOU_CHAIN_MASTER_MENU',
                'master_menu_version', 'v1',
                'schema_version', 'CHAIN_MASTER_MENU_V1',
                'fingerprint_sha256', 'e55ad23773753ade22e3e090622c361b58268ae5b43ee354ec7eef5b78f233f7',
                'source_profile_code', 'ST_DENIS_CANONICAL_PROFILE',
                'source_profile_version', 'v1'
            ),
            true
        ) AS content_json
    FROM source_version
)
INSERT INTO public.store_profile_versions (
    profile_id,
    profile_version,
    status,
    schema_version,
    content_json,
    fingerprint_sha256,
    source_reference,
    created_at,
    updated_at,
    published_at
)
SELECT
    profile_v2_content.profile_id,
    'v2',
    'DRAFT',
    'STORE_PROFILE_CONTRACT_V1',
    profile_v2_content.content_json::text,
    '2083269d602cf068b78551ee5d53916442dc262a77c4fa5eaef8eae5dc1267c2',
    'PHASE_B_PROFILE_V2_MASTER_MENU_REFERENCE:LANZHOU_CHAIN_MASTER_MENU/v1',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    NULL
FROM profile_v2_content
ON CONFLICT (profile_id, profile_version) DO NOTHING;

WITH source_version AS (
    SELECT source_profile_version.id
    FROM public.store_profiles profile
    JOIN public.store_profile_versions source_profile_version
        ON source_profile_version.profile_id = profile.id
    WHERE profile.profile_code = 'ST_DENIS_CANONICAL_PROFILE'
      AND source_profile_version.profile_version = 'v1'
),
target_version AS (
    SELECT target_profile_version.id, target_profile_version.status
    FROM public.store_profiles profile
    JOIN public.store_profile_versions target_profile_version
        ON target_profile_version.profile_id = profile.id
    WHERE profile.profile_code = 'ST_DENIS_CANONICAL_PROFILE'
      AND target_profile_version.profile_version = 'v2'
)
INSERT INTO public.store_profile_artifacts (
    profile_version_id,
    artifact_type,
    artifact_code,
    artifact_version,
    content_json,
    fingerprint_sha256,
    created_at,
    updated_at
)
SELECT
    target_version.id,
    artifact.artifact_type,
    artifact.artifact_code,
    artifact.artifact_version,
    artifact.content_json,
    artifact.fingerprint_sha256,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
FROM source_version
JOIN target_version ON target_version.status = 'DRAFT'
JOIN public.store_profile_artifacts artifact
    ON artifact.profile_version_id = source_version.id
ON CONFLICT (profile_version_id, artifact_type, artifact_code) DO NOTHING;

WITH target_version AS (
    SELECT target_profile_version.id, target_profile_version.status
    FROM public.store_profiles profile
    JOIN public.store_profile_versions target_profile_version
        ON target_profile_version.profile_id = profile.id
    WHERE profile.profile_code = 'ST_DENIS_CANONICAL_PROFILE'
      AND target_profile_version.profile_version = 'v2'
)
INSERT INTO public.store_profile_artifacts (
    profile_version_id,
    artifact_type,
    artifact_code,
    artifact_version,
    content_json,
    fingerprint_sha256,
    created_at,
    updated_at
)
SELECT
    target_version.id,
    'PRINTING_DISPLAY_RULES',
    'PRINTING_DISPLAY_RULES',
    'v1',
    $printing_display_rules$ {"schema_version":"PRINTING_DISPLAY_RULES_V1","outputs":["GRAB","FRONTDESK_RECEIPT","HOT_KITCHEN"],"item_aliases":[{"item_sku":"beef_chow_mein","outputs":{"GRAB":"牛炒","HOT_KITCHEN":"牛炒"}},{"item_sku":"chicken_chow_mein","outputs":{"GRAB":"鸡炒","HOT_KITCHEN":"鸡炒"}},{"item_sku":"tomato_chow_mein","outputs":{"GRAB":"番炒","HOT_KITCHEN":"番炒"}},{"item_sku":"vegetable_chow_mein","outputs":{"GRAB":"素炒","HOT_KITCHEN":"素炒"}},{"item_sku":"zha_jiang_noodle","outputs":{"GRAB":"炸","HOT_KITCHEN":"炸"}},{"item_sku":"dan_dan_noodle","outputs":{"GRAB":"担","HOT_KITCHEN":"担"}},{"item_sku":"cold_noodle_shredded_chicken","outputs":{"GRAB":"鸡凉","HOT_KITCHEN":"鸡凉"}},{"item_sku":"braised_beef_tendon_noodle","outputs":{"GRAB":"红","HOT_KITCHEN":"红"}},{"item_sku":"pickled_vegetable_beef_noodle","outputs":{"GRAB":"酸","HOT_KITCHEN":"酸"}},{"item_sku":"cucumber_salad","outputs":{"GRAB":"黄瓜","HOT_KITCHEN":"黄瓜"}},{"item_sku":"edamame","outputs":{"GRAB":"毛豆","HOT_KITCHEN":"毛豆"}},{"item_sku":"shredded_potato","outputs":{"GRAB":"土豆","HOT_KITCHEN":"土豆"}},{"item_sku":"braised_beef_shank_salad","outputs":{"GRAB":"牛展","HOT_KITCHEN":"牛展"}},{"item_sku":"traditional_beef_noodle","outputs":{"FRONTDESK_RECEIPT":"牛肉面"}}],"dictionaries":{"SIZE":[{"semantic_code":"SMALL","match_codes":["size_small","small"],"match_zh":["小","小碗"],"match_en":["small"],"outputs":{"GRAB":"小","HOT_KITCHEN":"小","FRONTDESK_RECEIPT_ZH":"小碗","FRONTDESK_RECEIPT_EN":"Small"}},{"semantic_code":"REGULAR","match_codes":["size_regular","regular","standard"],"match_zh":["中","中碗","标准","标准碗"],"match_en":["regular","standard"],"outputs":{"GRAB":"中","HOT_KITCHEN":"中","FRONTDESK_RECEIPT_ZH":"中碗","FRONTDESK_RECEIPT_EN":"Regular"}},{"semantic_code":"LARGE","match_codes":["size_large","large"],"match_zh":["大","大碗"],"match_en":["large"],"outputs":{"GRAB":"大","HOT_KITCHEN":"大","FRONTDESK_RECEIPT_ZH":"大碗","FRONTDESK_RECEIPT_EN":"Large"}}],"NOODLE_TYPE":[{"semantic_code":"ER_XI","match_zh":["二细"],"outputs":{"GRAB":"二","HOT_KITCHEN":"二"}},{"semantic_code":"SAN_XI","match_zh":["三细"],"outputs":{"GRAB":"三","HOT_KITCHEN":"三"}},{"semantic_code":"XI","match_zh":["细","细面"],"outputs":{"GRAB":"细","HOT_KITCHEN":"细"}},{"semantic_code":"MAO_XI","match_zh":["毛细"],"outputs":{"GRAB":"毛","HOT_KITCHEN":"毛"}},{"semantic_code":"LEEK_LEAF","match_zh":["韭叶","韭页"],"outputs":{"GRAB":"韭","HOT_KITCHEN":"韭"}},{"semantic_code":"WIDE","match_zh":["宽"],"outputs":{"GRAB":"宽","HOT_KITCHEN":"宽"}},{"semantic_code":"EXTRA_WIDE","match_zh":["大宽"],"outputs":{"GRAB":"大宽","HOT_KITCHEN":"大宽"}}],"SPICINESS":[{"semantic_code":"NO_SPICY","match_codes":["spicy_none"],"match_zh":["不辣"],"outputs":{"GRAB":"","HOT_KITCHEN":"","FRONTDESK_RECEIPT":"不辣"}},{"semantic_code":"LESS_SPICY","match_codes":["spicy_less"],"match_zh":["少辣"],"outputs":{"GRAB":"（少s）","HOT_KITCHEN":"（少s）","FRONTDESK_RECEIPT":"少辣"}},{"semantic_code":"REGULAR_SPICY","match_codes":["spicy_regular"],"match_zh":["正常辣"],"outputs":{"GRAB":"（s）","HOT_KITCHEN":"（s）","FRONTDESK_RECEIPT":"正常辣"}},{"semantic_code":"EXTRA_SPICY","match_codes":["spicy_extra"],"match_zh":["加辣"],"outputs":{"GRAB":"（大s）","HOT_KITCHEN":"（大s）","FRONTDESK_RECEIPT":"加辣"}}],"MODIFIER_ADD":[["extra_noodle","+面"],["tea_egg","+蛋"],["combo_tea_egg","+蛋"],["fried_egg","+煎"],["combo_fried_egg","+煎"],["extra_meat","+肉"],["extra_radish","+萝"],["bok_choy","加上海青"],["cilantro","+香"],["green_onion","+葱"],["extra_sauce","+酱"],["broccoli","+西兰"],["cabbage","+包"],["corn","+玉"],["seaweed","+海"],["mushroom","+菇"],["carrot_slice","+胡"],["combo_edamame","+毛豆"],["combo_shredded_potato","+土豆"],["combo_cucumber_salad","+黄瓜"]],"MODIFIER_REMOVE":[["cilantro","走香"],["green_onion","走葱"],["beef","走牛"],["radish","走萝"],["noodle","走面"],["less_noodle","少面"],["bok_choy","走上海青"],["broccoli","走西兰"],["corn","走玉米"],["mushroom","走菇"],["seaweed","走海"],["carrot","走胡"],["cucumber","走黄瓜"],["edamame","走毛豆"],["peanut","走花生"],["cabbage","走包"],["meat","走肉"],["green_pepper","走青椒"]],"SOUP_BASE":[{"semantic_code":"VEGETARIAN_SOUP","match_zh":["素汤"],"outputs":{"GRAB":"素","HOT_KITCHEN":"素"}},{"semantic_code":"BEEF_SOUP","match_zh":["肉汤","牛汤"],"outputs":{"GRAB":"素（肉汤）","HOT_KITCHEN":"素（肉汤）"}}],"COMBO":[{"semantic_code":"NO_EGG","outputs":{"FRONTDESK_RECEIPT":"走蛋"}},{"semantic_code":"EGG_LABEL","outputs":{"FRONTDESK_RECEIPT":"鸡蛋"}},{"semantic_code":"SIDE_LABEL","outputs":{"FRONTDESK_RECEIPT":"小菜"}}]},"conditional_overrides":[{"condition":{"item_sku":["traditional_beef_noodle","braised_beef_tendon_noodle","pickled_vegetable_beef_noodle","vegetable_noodle","dan_dan_noodle"],"dictionary":"NOODLE_TYPE","semantic_code":"SAN_XI"},"omit":true},{"condition":{"item_sku":"zha_jiang_noodle","dictionary":"NOODLE_TYPE","semantic_code":"LEEK_LEAF"},"omit":true},{"condition":{"item_sku":"cold_noodle_shredded_chicken","dictionary":"NOODLE_TYPE","semantic_code":"XI"},"omit":true}],"formatting":{"fried_quantity_symbol":"×","single_noodle_quantity":"×1","multi_noodle_quantity":"×","addon_quantity_marker":"x","green_compression":"ONION_CILANTRO_TO_QING","frontdesk_combo_prefix":"combo"}} $printing_display_rules$,
    '442214c92fabc31801d5e9aff9e08b97eadd404b91141ad9efe28180fea081a0',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
FROM target_version
WHERE target_version.status = 'DRAFT'
ON CONFLICT (profile_version_id, artifact_type, artifact_code) DO NOTHING;

UPDATE public.store_profile_versions target_profile_version
SET status = 'READY',
    published_at = CURRENT_TIMESTAMP,
    updated_at = CURRENT_TIMESTAMP
FROM public.store_profiles profile
WHERE target_profile_version.profile_id = profile.id
  AND profile.profile_code = 'ST_DENIS_CANONICAL_PROFILE'
  AND target_profile_version.profile_version = 'v2'
  AND target_profile_version.status = 'DRAFT';
