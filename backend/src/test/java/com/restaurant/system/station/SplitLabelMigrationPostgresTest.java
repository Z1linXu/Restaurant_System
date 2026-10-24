package com.restaurant.system.station;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import java.util.List;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.dao.DataIntegrityViolationException;

/** Run against a fresh, isolated local PostgreSQL database, never a shared environment. */
@EnabledIfEnvironmentVariable(named = "SPLIT_LABEL_MIGRATION_POSTGRES_URL", matches = "jdbc:postgresql://127\\.0\\.0\\.1:.*")
class SplitLabelMigrationPostgresTest {
    @Test
    void upgradePreservesEveryOldTableAndOrderFieldAndDefaultsAllStores() {
        String url = System.getenv("SPLIT_LABEL_MIGRATION_POSTGRES_URL");
        var source = new DriverManagerDataSource(url, System.getenv("USER"), "");
        Flyway.configure().dataSource(source).target("38").load().migrate();
        JdbcTemplate db = new JdbcTemplate(source);
        Long org = db.queryForObject("insert into organizations(name,code,status) values ('Split migration','SPLIT-MIG','active') returning id", Long.class);
        for (String code : List.of("SAINT_DENIS", "CHINATOWN", "SAINTE_CATHERINE")) {
            Long store = db.queryForObject("insert into stores(organization_id,name,code,status) values (?,?,?,'active') returning id", Long.class, org, code, code);
            db.update("insert into dining_tables(store_id,table_code,table_name,table_config,capacity,area_name,sort_order,supports_split,is_active) values (?,'T12','1','split_supported',4,'Main Hall',7,true,true)", store);
            db.update("insert into orders(store_id,order_no,order_type,status,table_no,subtotal_amount,total_amount) values (?,?,'dine_in','submitted','1-A',10,11.50)", store, code);
        }
        List<Map<String, Object>> beforeTables = db.queryForList("select * from dining_tables order by id");
        List<Map<String, Object>> beforeOrders = db.queryForList("select * from orders order by id");
        var flyway = Flyway.configure().dataSource(source).target("39").load();
        assertThat(flyway.migrate().migrationsExecuted).isEqualTo(1);
        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("39");
        flyway.validate();
        var after = db.queryForList("select * from dining_tables order by id");
        after.forEach(row -> assertThat(row.remove("split_label_mode")).isEqualTo("LEFT_RIGHT"));
        assertThat(after).isEqualTo(beforeTables);
        assertThat(db.queryForList("select * from orders order by id")).isEqualTo(beforeOrders);
        Long tableId = db.queryForObject("select min(id) from dining_tables", Long.class);
        db.update("update dining_tables set split_label_mode='INSIDE_OUTSIDE' where id=?", tableId);
        assertThat(db.queryForObject("select count(*) from dining_tables where split_label_mode='INSIDE_OUTSIDE'", Integer.class)).isEqualTo(1);
        assertThatThrownBy(() -> db.update("update dining_tables set split_label_mode='INVALID' where id=?", tableId))
            .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> db.update("update dining_tables set split_label_mode=null where id=?", tableId))
            .isInstanceOf(DataIntegrityViolationException.class);
        db.update("insert into dining_tables(table_code,table_name) values ('legacy-client','2')");
        assertThat(db.queryForObject("select split_label_mode from dining_tables where table_code='legacy-client'", String.class)).isEqualTo("LEFT_RIGHT");
    }
}
