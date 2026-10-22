package com.restaurant.system.menu.remove;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurant.system.menu.service.MenuRevisionService;
import jakarta.persistence.EntityManager;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@EnabledIfEnvironmentVariable(named="REMOVE_TEST_POSTGRES_URL",matches="jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/[a-zA-Z0-9_]+")
class StoreRemoveBaselinePostgresTest {
    @Configuration @EnableTransactionManagement(proxyTargetClass=true) static class Transactions {}

    @Test void baselinePreservesRawFieldsInactiveChoicesHistoryAndOwnerCanExplicitlyAllowLater() throws Exception {
        fresh(ds->{
            JdbcTemplate jdbc=new JdbcTemplate(ds);long store=store(jdbc,"baseline");
            long first=item(jdbc,store,"first"),inactive=item(jdbc,store,"inactive"),unassigned=item(jdbc,store,"unassigned");
            long active=option(jdbc,first,"remove_cilantro","走香菜",true);
            long inactiveOption=option(jdbc,inactive,"remove_cilantro","走香菜",false);
            long beef=option(jdbc,first,"remove_beef","走牛肉",true);
            long snapshot=id(jdbc,"insert into order_item_options(option_id,option_code_snapshot,option_name_snapshot_zh,price_delta,quantity) values (?,'remove_cilantro','历史香菜',0,1) returning id",active);
            var history=jdbc.queryForMap("select * from order_item_options where id=?",snapshot);
            try(Harness h=new Harness(ds)) {
                var dry=h.baseline.reconcile(request(store,true,null),null);
                assertThat(dry.created_master_count()).isEqualTo(18);assertThat(dry.linked_option_count()).isEqualTo(3);
                assertThat(jdbc.queryForObject("select count(*) from store_removes",Integer.class)).isZero();
                assertThat(jdbc.queryForObject("select count(*) from audit_logs",Integer.class)).isZero();
                var apply=h.baseline.reconcile(request(store,false,dry.before_fingerprint()),null);
                assertThat(apply.menu_item_eligibility_changes()).isZero();
                assertThat(apply.before().stream().map(StoreRemoveBaselineReconciler.OptionSnapshot::invariant).toList())
                    .isEqualTo(apply.after().stream().map(StoreRemoveBaselineReconciler.OptionSnapshot::invariant).toList());
                assertThat(jdbc.queryForObject("select option_code from menu_item_options where id=?",String.class,active)).isEqualTo("remove_cilantro");
                assertThat(jdbc.queryForObject("select name_zh from menu_item_options where id=?",String.class,active)).isEqualTo("走香菜");
                long cilantro=id(jdbc,"select id from store_removes where store_id=? and code='cilantro'",store);
                assertThat(jdbc.queryForObject("select mode from store_remove_item_rules where remove_id=? and menu_item_id=?",String.class,cilantro,inactive)).isEqualTo("DENY");
                h.catalog.refreshStoreAssignments(store);
                assertThat(jdbc.queryForObject("select is_active from menu_item_options where id=?",Boolean.class,inactiveOption)).isFalse();
                assertThat(jdbc.queryForObject("select count(*) from menu_item_options where menu_item_id=?",Integer.class,unassigned)).isZero();
                assertThat(jdbc.queryForMap("select * from order_item_options where id=?",snapshot)).isEqualTo(history);
                assertThat(jdbc.queryForObject("select count(*) from store_removes where code in ('beef','meat') and store_id=?",Integer.class,store)).isEqualTo(2);
                long beforeAudit=jdbc.queryForObject("select count(*) from audit_logs",Long.class);
                var replayDry=h.baseline.reconcile(request(store,true,null),null);
                var replay=h.baseline.reconcile(request(store,false,replayDry.before_fingerprint()),null);
                assertThat(replay.created_master_count()).isZero();assertThat(replay.linked_option_count()).isZero();
                assertThat(jdbc.queryForObject("select count(*) from audit_logs",Long.class)).isEqualTo(beforeAudit);
                var rules=new StoreRemoveService.ApplicabilityRequest();rules.category_ids=List.of();rules.items=new ArrayList<>();
                for(var rule:h.catalog.getApplicability(cilantro).items()) {
                    var next=new StoreRemoveService.RuleRequest();next.item_id=rule.item_id();next.mode=rule.item_id().equals(inactive)?"ALLOW":rule.mode();rules.items.add(next);
                }
                h.catalog.setApplicability(cilantro,rules);
                assertThat(jdbc.queryForObject("select is_active from menu_item_options where id=?",Boolean.class,inactiveOption)).isTrue();
                assertThat(jdbc.queryForObject("select option_code from menu_item_options where id=?",String.class,beef)).isEqualTo("remove_beef");
                var write=new StoreRemoveService.WriteRequest();write.store_id=store;write.code="remove_cilantro";write.name_zh="重复";write.active=true;
                assertThatThrownBy(()->h.catalog.create(write)).hasMessageContaining("REMOVE_CODE_EXISTS");
            }
        });
    }

    @Test void staleFingerprintUnexpectedOptionMutationAndAuditFailureRollBackWholeStore() throws Exception {
        fresh(ds->{
            JdbcTemplate jdbc=new JdbcTemplate(ds);long store=store(jdbc,"rollback"),item=item(jdbc,store,"dish");
            long option=option(jdbc,item,"remove_cilantro","走香菜",true);
            try(Harness h=new Harness(ds)) {
                var before=h.baseline.reconcile(request(store,true,null),null);
                jdbc.update("update menu_item_options set name_en='New current name' where id=?",option);
                assertThatThrownBy(()->h.baseline.reconcile(request(store,false,before.before_fingerprint()),null)).hasMessageContaining("REMOVE_BASELINE_CHANGED");
                var latest=h.baseline.reconcile(request(store,true,null),null);
                jdbc.execute("create function change_remove_name() returns trigger language plpgsql as $$ begin new.name_zh := 'UNEXPECTED'; return new; end $$");
                jdbc.execute("create trigger change_remove_name before update on menu_item_options for each row execute function change_remove_name()");
                assertThatThrownBy(()->h.baseline.reconcile(request(store,false,latest.before_fingerprint()),null)).hasMessageContaining("REMOVE_BASELINE_INVARIANT_CHANGED");
                assertThat(jdbc.queryForObject("select count(*) from store_removes",Integer.class)).isZero();
                assertThat(jdbc.queryForObject("select name_zh from menu_item_options where id=?",String.class,option)).isEqualTo("走香菜");
                jdbc.execute("drop trigger change_remove_name on menu_item_options");
                jdbc.execute("create function fail_remove_audit() returns trigger language plpgsql as $$ begin raise exception 'AUDIT_REQUIRED_TEST'; end $$");
                jdbc.execute("create trigger fail_remove_audit before insert on audit_logs for each row execute function fail_remove_audit()");
                assertThatThrownBy(()->h.baseline.reconcile(request(store,false,latest.before_fingerprint()),null)).hasMessageContaining("AUDIT_REQUIRED_TEST");
                assertThat(jdbc.queryForObject("select count(*) from store_removes",Integer.class)).isZero();
                assertThat(jdbc.queryForObject("select store_remove_id from menu_item_options where id=?",Long.class,option)).isNull();
                assertThat(jdbc.queryForObject("select count(*) from store_remove_item_rules",Integer.class)).isZero();
            }
        });
    }

    @Test void reusePrefixedCustomMasterAndRetainAmbiguousAndNonBaselineRows() throws Exception {
        fresh(ds->{
            JdbcTemplate jdbc=new JdbcTemplate(ds);long store=store(jdbc,"custom"),item=item(jdbc,store,"first"),other=item(jdbc,store,"second");
            option(jdbc,item,"remove_cilantro","走香菜",true);
            option(jdbc,item,"remove_carrot","走胡萝卜",true);option(jdbc,other,"remove_carrot","走胡萝卜片",true);
            option(jdbc,item,"remove_crushed_peanut","走花生碎",true);
            try(Harness h=new Harness(ds)) {
                // Existing master is deliberately Store-customized and must be reused unchanged.
                long custom=id(jdbc,"insert into store_removes(store_id,code,name_zh,name_en,active,sort_order,created_at,updated_at) values (?,'remove_cilantro','私人名称','Private',true,333,current_timestamp,current_timestamp) returning id",store);
                var dry=h.baseline.reconcile(request(store,true,null),null);
                assertThat(dry.created_master_count()).isEqualTo(17);assertThat(dry.linked_option_count()).isOne();
                var apply=h.baseline.reconcile(request(store,false,dry.before_fingerprint()),null);
                assertThat(apply.conflicts()).anyMatch(c->"carrot".equals(c.semantic_code()) && "REMOVE_BUSINESS_VALUES_CONFLICT".equals(c.reason()));
                assertThat(jdbc.queryForMap("select code,name_zh,name_en,sort_order from store_removes where id=?",custom))
                    .containsEntry("code","remove_cilantro").containsEntry("name_zh","私人名称").containsEntry("name_en","Private").containsEntry("sort_order",333);
                assertThat(jdbc.queryForObject("select count(*) from store_removes where store_id=?",Integer.class,store)).isEqualTo(18);
                assertThat(jdbc.queryForObject("select count(*) from store_removes where code='crushed_peanut'",Integer.class)).isZero();
                assertThat(jdbc.queryForObject("select count(*) from menu_item_options where store_remove_id is null",Integer.class)).isEqualTo(3);
                var duplicate = new StoreRemoveService.WriteRequest();duplicate.store_id=store;duplicate.code="cilantro";duplicate.name_zh="重复";duplicate.active=true;
                assertThatThrownBy(()->h.catalog.create(duplicate)).hasMessageContaining("REMOVE_CODE_EXISTS");
            }
        });
    }

    /** Optional private preflight input stays on the caller's machine, never a committed fixture. */
    @Test
    @EnabledIfEnvironmentVariable(named="REMOVE_PREFLIGHT_JSON",matches=".+")
    void productionPreflight330RowsGiveThreeIndependent18By53PlansWithNoItemChanges() throws Exception {
        JsonNode source=new ObjectMapper().readTree(Files.readString(Path.of(System.getenv("REMOVE_PREFLIGHT_JSON"))));
        fresh(ds->{
            JdbcTemplate jdbc=new JdbcTemplate(ds);loadPreflight(jdbc,source);
            Map<String,Object> export=new LinkedHashMap<>();export.put("environment","DISPOSABLE_LOCAL_PREFLIGHT_REPLAY");export.put("production_mutated",false);
            List<Object> reports=new ArrayList<>();
            try(Harness h=new Harness(ds)) {
                for(JsonNode store:source.path("stores")) {
                    long id=store.path("id").asLong();
                    var dry=h.baseline.reconcile(request(id,true,null),null);
                    assertThat(dry.existing_shared_count()).isZero();assertThat(dry.created_master_count()).isEqualTo(18);assertThat(dry.linked_option_count()).isEqualTo(53);
                    assertThat(dry.before()).hasSize(110);assertThat(dry.menu_item_eligibility_changes()).isZero();
                    var apply=h.baseline.reconcile(request(id,false,dry.before_fingerprint()),null);
                    assertThat(apply.before().stream().map(StoreRemoveBaselineReconciler.OptionSnapshot::invariant).toList())
                        .isEqualTo(apply.after().stream().map(StoreRemoveBaselineReconciler.OptionSnapshot::invariant).toList());
                    assertThat(jdbc.queryForObject("select count(*) from store_removes where store_id=?",Integer.class,id)).isEqualTo(18);
                    assertThat(jdbc.queryForObject("select count(*) from menu_item_options o join menu_items i on i.id=o.menu_item_id where i.store_id=? and o.store_remove_id is not null",Integer.class,id)).isEqualTo(53);
                    if(id==2L) {
                        assertThat(jdbc.queryForList("select id from menu_item_options where id in (406,420) and is_active=false and store_remove_id is not null order by id",Long.class)).containsExactly(406L,420L);
                    }
                    var replayDry=h.baseline.reconcile(request(id,true,null),null);
                    var replay=h.baseline.reconcile(request(id,false,replayDry.before_fingerprint()),null);
                    assertThat(replay.created_master_count()).isZero();assertThat(replay.linked_option_count()).isZero();
                    Map<String,Object> evidence=new LinkedHashMap<>();evidence.put("store_id",id);evidence.put("store_name",store.path("name").asText());evidence.put("dry_run",dry);evidence.put("apply",apply);evidence.put("replay",replay);reports.add(evidence);
                }
            }
            export.put("stores",reports);
            String reportPath=System.getenv("REMOVE_PREFLIGHT_REPORT");
            if(reportPath!=null && !reportPath.isBlank()) Files.writeString(Path.of(reportPath),new ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(export));
        });
    }

    private static void loadPreflight(JdbcTemplate jdbc,JsonNode source) {
        jdbc.update("insert into organizations(id,name,code) values (1,'Local menu-only preflight','local_remove_preflight')");
        for(JsonNode store:source.path("stores")) jdbc.update("insert into stores(id,name,code,organization_id) values (?,?,?,1)",store.path("id").asLong(),store.path("name").asText(),"local_"+store.path("id").asText());
        Map<Long,JsonNode> items=new LinkedHashMap<>();Map<Long,Long> categories=new LinkedHashMap<>();
        for(JsonNode row:source.path("raw_remove_options")) {
            items.putIfAbsent(row.path("menu_item_id").asLong(),row);
            if(!row.path("category_id").isNull()) categories.putIfAbsent(row.path("category_id").asLong(),row.path("store_id").asLong());
        }
        categories.forEach((id,store)->jdbc.update("insert into menu_categories(id,store_id,name_zh,sort_order) values (?,?,'Local category',0)",id,store));
        items.forEach((id,row)->jdbc.update("insert into menu_items(id,store_id,category_id,sku,name_zh,name_en,is_active,sort_order) values (?,?,?,?,?,?,?,0)",id,row.path("store_id").asLong(),number(row,"category_id"),string(row,"item_sku"),string(row,"item_name_zh"),string(row,"item_name_en"),row.path("item_active").asBoolean()));
        for(JsonNode row:source.path("raw_remove_options")) if(!row.path("parent_option_id").isNull()) {
            jdbc.update("insert into menu_item_options(id,menu_item_id,option_type,option_group,option_code,name_zh,price_delta,is_active) values (?,?,'addon',?,?,?,0,true) on conflict(id) do nothing",row.path("parent_option_id").asLong(),row.path("parent_menu_item_id").asLong(),string(row,"parent_group"),string(row,"parent_code"),string(row,"parent_name_zh"));
        }
        for(JsonNode row:source.path("raw_remove_options")) jdbc.update("insert into menu_item_options(id,menu_item_id,option_type,option_group,option_code,name_zh,name_en,price_delta,is_active,parent_option_id) values (?,?,?,?,?,?,?,?,?,?)",row.path("option_id").asLong(),row.path("menu_item_id").asLong(),string(row,"option_type"),string(row,"option_group"),string(row,"option_code"),string(row,"name_zh"),string(row,"name_en"),row.path("price_delta").decimalValue(),row.path("is_active").asBoolean(),number(row,"parent_option_id"));
    }
    private static String string(JsonNode row,String key) { return row.path(key).isNull() || row.path(key).isMissingNode()?null:row.path(key).asText(); }
    private static Long number(JsonNode row,String key) { return row.path(key).isNull() || row.path(key).isMissingNode()?null:row.path(key).asLong(); }
    private static StoreRemoveBaselineReconciler.Request request(Long store,boolean dry,String fingerprint) {var r=new StoreRemoveBaselineReconciler.Request();r.store_id=store;r.dry_run=dry;r.expected_before_fingerprint=fingerprint;return r;}
    private static long store(JdbcTemplate jdbc,String key) {long org=id(jdbc,"insert into organizations(name,code) values ('Local',?) returning id",key);return id(jdbc,"insert into stores(name,code,organization_id) values ('Local',?,?) returning id",key,org);}
    private static long item(JdbcTemplate jdbc,Long store,String sku) {return id(jdbc,"insert into menu_items(store_id,sku,name_zh,is_active,sort_order) values (?,?,'Local',true,0) returning id",store,sku);}
    private static long option(JdbcTemplate jdbc,Long item,String code,String name,boolean active) {return id(jdbc,"insert into menu_item_options(menu_item_id,option_type,option_group,option_code,name_zh,name_en,price_delta,is_active,sort_order) values (?,'remove','REMOVE',?,?,'Original English',0,?,73) returning id",item,code,name,active);}
    private static long id(JdbcTemplate jdbc,String sql,Object...args) {return jdbc.queryForObject(sql,Long.class,args);}
    private static class Harness implements AutoCloseable {
        final AnnotationConfigApplicationContext context=new AnnotationConfigApplicationContext();final StoreRemoveService catalog;final StoreRemoveBaselineReconciler baseline;
        Harness(DriverManagerDataSource ds) {
            JdbcTemplate jdbc=new JdbcTemplate(ds);MenuRevisionService revisions=mock(MenuRevisionService.class);
            when(revisions.lockStoresInOrder(any())).thenAnswer(inv->{List<Long> ids=inv.getArgument(0);for(Long id:ids)jdbc.queryForList("select id from stores where id=? for update",id);return List.of();});
            doAnswer(inv->{jdbc.update("update stores set menu_revision=menu_revision+1 where id=?",inv.getArgument(0,Long.class));return null;}).when(revisions).incrementRevision(anyLong());
            context.register(Transactions.class);context.registerBean("transactionManager",DataSourceTransactionManager.class,()->new DataSourceTransactionManager(ds));
            context.registerBean(StoreRemoveService.class,()->new StoreRemoveService(jdbc,revisions,mock(EntityManager.class)));
            context.registerBean(StoreRemoveBaselineReconciler.class,()->new StoreRemoveBaselineReconciler(jdbc,revisions,mock(EntityManager.class)));
            context.refresh();catalog=context.getBean(StoreRemoveService.class);baseline=context.getBean(StoreRemoveBaselineReconciler.class);
        }
        public void close(){context.close();}
    }
    private static void fresh(DatabaseTest test) throws Exception {
        String url=System.getenv("REMOVE_TEST_POSTGRES_URL"),user=System.getenv().getOrDefault("REMOVE_TEST_POSTGRES_USER",System.getProperty("user.name")),password=System.getenv().getOrDefault("REMOVE_TEST_POSTGRES_PASSWORD","");
        String name="remove_baseline_"+UUID.randomUUID().toString().replace("-","");
        try(Connection admin=DriverManager.getConnection(url,user,password)) {
            try(var s=admin.createStatement()){s.execute("create database "+name);}
            try {var ds=new DriverManagerDataSource(url.substring(0,url.lastIndexOf('/')+1)+name,user,password);Flyway.configure().dataSource(ds).locations("classpath:db/migration").load().migrate();test.run(ds);}
            finally {try(var s=admin.createStatement()){s.execute("drop database "+name);}}
        }
    }
    @FunctionalInterface private interface DatabaseTest {void run(DriverManagerDataSource ds) throws Exception;}
}
