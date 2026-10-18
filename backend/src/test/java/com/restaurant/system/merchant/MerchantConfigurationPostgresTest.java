package com.restaurant.system.merchant;

import static org.assertj.core.api.Assertions.*;
import com.restaurant.system.owner.profile.NeutralRestaurantProfile;
import com.restaurant.system.support.CommercialPostgres;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;

@EnabledIfEnvironmentVariable(named = "COMMERCIAL_TEST_POSTGRES_URL", matches = "jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/commercial_[a-zA-Z0-9_]+")
class MerchantConfigurationPostgresTest {
    @Test void freshAndV38UpgradeKeepHistoryAndExplicitlyBackfillOnlyExistingStores() throws Exception {
        try (var db = new CommercialPostgres()) {
            Flyway.configure().dataSource(db.dataSource).target("38").load().migrate();
            var jdbc = new JdbcTemplate(db.dataSource);
            long org = jdbc.queryForObject("insert into organizations(name,code) values ('Synthetic Merchant','synthetic_merchant') returning id", Long.class);
            long legacy = jdbc.queryForObject("insert into stores(name,code,organization_id) values ('Synthetic Legacy','synthetic_legacy',?) returning id", Long.class, org);
            long neutral = jdbc.queryForObject("insert into stores(name,code,organization_id,provisioning_source) values ('Synthetic New','synthetic_new',?,'PHASE_B_OWNER_PROVISIONING') returning id", Long.class, org);
            long verified = jdbc.queryForObject("""
                insert into stores(name,code,organization_id,provisioning_source,provisioned_profile_code,provisioned_profile_version,provisioned_profile_fingerprint_sha256)
                select 'Synthetic Verified','synthetic_verified',?,'PHASE_B_OWNER_PROVISIONING',p.profile_code,v.profile_version,v.fingerprint_sha256
                from store_profiles p join store_profile_versions v on v.profile_id=p.id
                where p.profile_code='ST_DENIS_CANONICAL_PROFILE' and v.profile_version='v2' returning stores.id
                """, Long.class, org);
            seedHistory(jdbc, legacy, org);
            Map<String, List<String>> originalColumns = historyColumns(jdbc);
            Map<String,String> before = history(jdbc, originalColumns);
            var profileBefore = jdbc.queryForList("select id,content_json,fingerprint_sha256 from store_profile_versions order by id");
            var flyway = Flyway.configure().dataSource(db.dataSource).load(); flyway.migrate(); flyway.validate();
            assertThat(history(jdbc, originalColumns)).isEqualTo(before);
            assertNewMenuColumnsHaveCompatibilityDefaults(jdbc);
            assertThat(jdbc.queryForList("select id,content_json,fingerprint_sha256 from store_profile_versions where profile_id in (select id from store_profiles where profile_code <> 'NEUTRAL_RESTAURANT') order by id")).isEqualTo(profileBefore);
            assertThat(mode(jdbc, legacy)).isEqualTo("PROVISIONAL_COMPATIBILITY");
            assertThat(mode(jdbc, verified)).isEqualTo("PROVISIONAL_COMPATIBILITY");
            assertThat(mode(jdbc, neutral)).isEqualTo("NEUTRAL");
            assertThat(jdbc.queryForObject("select configuration_source from store_merchant_configurations where store_id=?", String.class, legacy)).isEqualTo("LEGACY_INSTALLATION_BACKFILL");
            long after = jdbc.queryForObject("insert into stores(name,code,organization_id) values ('New legacy-looking store','synthetic_after',?) returning id", Long.class, org);
            assertThat(jdbc.queryForObject("select count(*) from store_merchant_configurations where store_id=?", Integer.class, after)).isZero();
            assertThat(jdbc.queryForObject("select content_json from store_profile_versions where profile_id=(select id from store_profiles where profile_code='NEUTRAL_RESTAURANT')", String.class)).isEqualTo(NeutralRestaurantProfile.CONTENT);
            assertThat(jdbc.queryForObject("select count(*) from store_profile_artifacts where profile_version_id in (select v.id from store_profile_versions v join store_profiles p on p.id=v.profile_id where p.profile_code='NEUTRAL_RESTAURANT')", Integer.class)).isZero();
            assertNeutralAggregateFingerprint(jdbc);
            assertThat(flyway.migrate().migrationsExecuted).isZero();
        }
        try (var db = new CommercialPostgres()) {
            var flyway = Flyway.configure().dataSource(db.dataSource).load(); flyway.migrate(); flyway.validate();
            var jdbc = new JdbcTemplate(db.dataSource);
            assertThat(jdbc.queryForObject("select count(*) from store_profiles where visibility='PUBLIC_PRESET'", Integer.class)).isEqualTo(1);
            assertThat(jdbc.queryForObject("select fingerprint_sha256 from store_profile_versions where profile_id=(select id from store_profiles where profile_code='NEUTRAL_RESTAURANT')", String.class)).isEqualTo(NeutralRestaurantProfile.FINGERPRINT);
            assertNeutralAggregateFingerprint(jdbc);
        }
    }
    private void assertNeutralAggregateFingerprint(JdbcTemplate jdbc) {
        var row = jdbc.queryForMap("""
            select p.profile_code,v.profile_version,v.schema_version,v.content_json,v.fingerprint_sha256
            from store_profile_versions v join store_profiles p on p.id=v.profile_id
            where p.profile_code='NEUTRAL_RESTAURANT' and v.profile_version='v1'
            """);
        var validator = new com.restaurant.system.owner.profile.StoreProfileContractValidator();
        String content = (String) row.get("content_json");
        String aggregate = validator.computeAggregateFingerprint((String) row.get("profile_code"),
            (String) row.get("profile_version"), (String) row.get("schema_version"), content, java.util.List.of());
        // Profile fingerprints include identity/schema/artifacts; a raw JSON hash is a different contract.
        assertThat(row.get("fingerprint_sha256")).isEqualTo(aggregate);
        assertThat(aggregate).isEqualTo(NeutralRestaurantProfile.FINGERPRINT);
        String contentOnly = com.restaurant.system.owner.profile.StoreProfileCanonicalJson.sha256Canonical(content);
        assertThat(contentOnly).isNotEqualTo(aggregate);
        assertThat(validator.validate(NeutralRestaurantProfile.CODE, "v1", NeutralRestaurantProfile.SCHEMA,
            content, aggregate, java.util.List.of()).valid()).isTrue();
        assertThat(validator.validate(NeutralRestaurantProfile.CODE, "v1", NeutralRestaurantProfile.SCHEMA,
            content, contentOnly, java.util.List.of()).valid()).isFalse();
    }
    private String mode(JdbcTemplate jdbc, long store) { return jdbc.queryForObject("select compatibility_mode from store_merchant_configurations where store_id=?", String.class, store); }
    static void seedHistory(JdbcTemplate jdbc, long store, long org) {
        long menu = jdbc.queryForObject("insert into menu_items(store_id,sku,name_en,name_zh,item_type,base_price,is_active,sort_order) values (?,'SYNTHETIC_HISTORY','Old generic item','旧通用菜品','MAIN',12.34,true,10) returning id", Long.class, store);
        long option = jdbc.queryForObject("insert into menu_item_options(menu_item_id,option_code,option_group,option_type,name_en,price_delta,is_active) values (?,'OLD_GENERIC','ADD_ON','addon','Old generic option',0.50,true) returning id", Long.class, menu);
        long order = jdbc.queryForObject("insert into orders(store_id,order_no,order_type,status,subtotal_amount,total_amount,current_revision) values (?,'SYNTHETIC_HISTORY','TAKEOUT','COMPLETED',12.84,14.76,1) returning id", Long.class, store);
        long item = jdbc.queryForObject("insert into order_items(order_id,menu_item_id,item_name_snapshot_en,item_name_snapshot_zh,quantity,unit_price,line_amount,status) values (?,?,'Frozen generic item','冻结通用菜品',1,12.34,12.34,'COMPLETED') returning id", Long.class, order,menu);
        jdbc.update("insert into order_item_options(order_item_id,option_id,option_name_snapshot_en,option_code_snapshot,price_delta,quantity) values (?,?,'Frozen generic option','OLD_GENERIC',0.50,1)",item,option);
        jdbc.update("insert into kitchen_tasks(store_id,order_id,order_item_id,station_code,item_name_snapshot_en,status,quantity) values (?,?,?,'SYNTHETIC','Frozen generic task','COMPLETED',1)",store,order,item);
        jdbc.update("insert into print_jobs(store_id,organization_id,order_id,module_code,receipt_type,status,payload_snapshot,rendered_text_snapshot,escpos_payload_base64,manual_request_hash) values (?,?,?,'GRAB','ORDER','PRINTED','{\"synthetic_frozen\":true}','Frozen generic ticket','c3ludGhldGlj',repeat('a',64))",store,org,order);
    }
    static Map<String,String> history(JdbcTemplate jdbc) {
        Map<String,String> hashes = new LinkedHashMap<>();
        for (String table : new String[]{"orders","order_items","order_item_options","kitchen_tasks","print_jobs","menu_items","menu_item_options","chain_master_menu_products"}) {
            hashes.put(table, jdbc.queryForObject("select md5(coalesce(json_agg(t order by t.id)::text,'null')) from " + table + " t", String.class));
        }
        return hashes;
    }

    private static Map<String, List<String>> historyColumns(JdbcTemplate jdbc) {
        Map<String, List<String>> columns = new LinkedHashMap<>();
        for (String table : new String[]{"orders","order_items","order_item_options","kitchen_tasks","print_jobs","menu_items","menu_item_options","chain_master_menu_products"}) {
            columns.put(table, jdbc.queryForList("select column_name from information_schema.columns where table_schema='public' and table_name=? order by ordinal_position", String.class, table));
        }
        return columns;
    }

    /** Compare every pre-migration value; additional nullable columns are checked independently. */
    private static Map<String,String> history(JdbcTemplate jdbc, Map<String, List<String>> columns) {
        Map<String,String> hashes = new LinkedHashMap<>();
        columns.forEach((table, names) -> {
            String projection = names.stream().map(name -> "\"" + name.replace("\"", "\"\"") + "\"").collect(java.util.stream.Collectors.joining(","));
            hashes.put(table, jdbc.queryForObject("select md5(coalesce(json_agg(t order by t.id)::text,'null')) from (select " + projection + " from " + table + ") t", String.class));
        });
        return hashes;
    }

    private void assertNewMenuColumnsHaveCompatibilityDefaults(JdbcTemplate jdbc) {
        for (String table : new String[]{"menu_categories", "menu_items", "menu_item_options", "store_addons", "store_combo_groups", "store_combo_components"}) {
            assertThat(jdbc.queryForObject("select count(*) from " + table + " where name_fr is not null", Long.class)).as(table + " FR remains absent for legacy rows").isZero();
        }
        assertThat(jdbc.queryForObject("select count(*) from menu_items where customization_config is not null or sellable is distinct from true", Long.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from order_items where item_name_snapshot_fr is not null", Long.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from order_item_options where option_name_snapshot_fr is not null or component_id_snapshot is not null or linked_menu_item_id_snapshot is not null or linked_station_id_snapshot is not null", Long.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from kitchen_tasks where component_option_id is not null", Long.class)).isZero();
    }
}
