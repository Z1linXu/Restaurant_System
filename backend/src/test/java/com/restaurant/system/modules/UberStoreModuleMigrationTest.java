package com.restaurant.system.modules;

import static org.assertj.core.api.Assertions.assertThat;
import java.sql.DriverManager;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

@EnabledIfEnvironmentVariable(named="UBER_TEST_POSTGRES_URL", matches="jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/[a-zA-Z0-9_]+")
class UberStoreModuleMigrationTest {
    @Test void upgradeDefaultsOffAndOwnerReconciliationPreservesIdentityPriceAndHistory() throws Exception {
        String adminUrl=System.getenv("UBER_TEST_POSTGRES_URL");
        String user=System.getenv("USER");
        String name="uber_module_"+UUID.randomUUID().toString().replace("-", "");
        try (var admin=DriverManager.getConnection(adminUrl,user,"")) {
            try(var statement=admin.createStatement()) {statement.execute("CREATE DATABASE "+name);}
            try {
                var ds=new DriverManagerDataSource(adminUrl.substring(0,adminUrl.lastIndexOf('/')+1)+name,user,"");
                Flyway.configure().dataSource(ds).target("36").load().migrate();
                var db=new JdbcTemplate(ds);
                db.update("insert into organizations(id,name,code) values(1,'Fixture','FIXTURE')");
                db.update("insert into stores(id,organization_id,name,code) values(1,1,'Fixture','STG005_SRC_20260809_R01'),(18,1,'Disabled fixture','DISABLED_FIXTURE')");
                db.update("insert into menu_items(id,store_id,sku,name_zh,sort_order) values(25,1,'braised_beef_noodle','测试',0)");
                db.update("insert into menu_item_options(id,menu_item_id,option_type,name_zh,name_en,price_delta,is_active) values(370,25,'addon','加蛋','Extra Egg',1.99,true)");
                db.update("insert into order_item_options(option_id,option_name_snapshot_zh,option_name_snapshot_en,price_delta,quantity) values(370,'历史蛋','Historical egg',2.49,1)");
                var historical=db.queryForList("select * from order_item_options");
                var option=db.queryForMap("select id,menu_item_id,price_delta,is_active,created_at from menu_item_options where id=370");
                var flyway=Flyway.configure().dataSource(ds).load();flyway.migrate();
                assertThat(db.queryForList("select enabled from store_modules where module_key='UBER_EATS' order by store_id",Boolean.class)).containsExactly(false,false);
                assertThat(flyway.migrate().migrationsExecuted).isZero();
                db.update("insert into organization_addon_definitions(id,organization_id,code,created_at,updated_at) values(19,1,'tea_egg',now(),now())");
                db.update("insert into store_addons(id,store_id,organization_id,organization_addon_definition_id,name_zh,name_en,price,active,created_at,updated_at) values(60,1,1,19,'加卤蛋','Extra Tea Egg',1.99,true,now(),now())");
                String sql=Files.readString(Path.of("../deployment/cloud/uber-capability-20261003/reconcile-option370.sql"))
                    .replace("current_database()<>'restaurant_pos_staging'","current_database()<>'"+name+"'");
                try(var c=ds.getConnection();var statement=c.createStatement()) {
                    c.setAutoCommit(false);statement.execute(sql);c.commit();
                }
                assertThat(db.queryForMap("select id,menu_item_id,price_delta,is_active,created_at from menu_item_options where id=370")).isEqualTo(option);
                assertThat(db.queryForList("select * from order_item_options")).isEqualTo(historical);
                assertThat(db.queryForMap("select option_code,option_group,option_type,name_zh,name_en,store_addon_id,addon_eligible from menu_item_options where id=370"))
                    .containsEntry("option_code","tea_egg").containsEntry("option_group","ADD_ON").containsEntry("option_type","addon")
                    .containsEntry("name_zh","加卤蛋").containsEntry("name_en","Extra Tea Egg").containsEntry("store_addon_id",60L).containsEntry("addon_eligible",true);
            } finally {
                try(var statement=admin.createStatement()) {statement.execute("DROP DATABASE "+name);}
            }
        }
    }
}
