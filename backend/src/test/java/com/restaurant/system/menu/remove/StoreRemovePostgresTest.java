package com.restaurant.system.menu.remove;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.restaurant.system.common.exception.BusinessException;
import com.restaurant.system.menu.entity.MenuItemOption;
import com.restaurant.system.menu.repository.MenuItemOptionRepository;
import com.restaurant.system.menu.service.MenuRevisionService;
import com.restaurant.system.menu.service.StoreComboConfigurationService;
import com.restaurant.system.order.dto.CreateOrderItemOptionRequest;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.List;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/** Opt-in disposable loopback PostgreSQL; never connects to an application database. */
@EnabledIfEnvironmentVariable(named="REMOVE_TEST_POSTGRES_URL",matches="jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/[a-zA-Z0-9_]+")
class StoreRemovePostgresTest {
    @Configuration @EnableTransactionManagement(proxyTargetClass=true) static class Transactions {}

    @Test void cleanStartupAndV37UpgradePreserveHistoryEnforceFreeScopeAndIdentity() throws Exception {
        fresh(ds->{
            migrate(ds,"37");JdbcTemplate jdbc=new JdbcTemplate(ds);Fixture f=fixture(jdbc);
            long option=id(jdbc,"insert into menu_item_options(menu_item_id,option_type,option_group,option_code,name_zh,price_delta,is_active) values (?,'remove','REMOVE','remove_cilantro','走香菜',0,true) returning id",f.item());
            long snapshot=id(jdbc,"insert into order_item_options(option_id,option_code_snapshot,option_name_snapshot_zh,price_delta,quantity) values (?,'remove_cilantro','历史香菜',0,1) returning id",option);
            var before=jdbc.queryForMap("select * from order_item_options where id=?",snapshot);
            migrate(ds,null);assertThat(migrate(ds,null)).isZero();
            assertThat(jdbc.queryForObject("select count(*) from store_removes",Integer.class)).isZero();
            try(Harness h=new Harness(ds)) {
                var report=h.service.reconcile(f.store(),false);assertThat(report.linked_options()).isOne();
                long remove=h.service.getRemoves(f.store()).removes().get(0).id();
                assertThatThrownBy(()->jdbc.update("update store_removes set code='other' where id=?",remove)).isInstanceOf(DataAccessException.class).hasMessageContaining("REMOVE_CODE_IMMUTABLE");
                assertThatThrownBy(()->jdbc.update("update menu_item_options set price_delta=1 where id=?",option)).isInstanceOf(DataAccessException.class);
                assertThatThrownBy(()->jdbc.update("update menu_item_options set price_delta=null where id=?",option)).isInstanceOf(DataAccessException.class);
                assertThatThrownBy(()->jdbc.update("update menu_item_options set menu_item_id=? where id=?",f.otherItem(),option)).isInstanceOf(DataAccessException.class);
                assertThatThrownBy(()->jdbc.update("insert into store_remove_categories values (?,?,?)",remove,f.store(),f.otherCategory())).isInstanceOf(DataAccessException.class);
                assertThatThrownBy(()->jdbc.update("insert into store_remove_item_rules values (?,?,?,'ALLOW')",remove,f.store(),f.otherItem())).isInstanceOf(DataAccessException.class);
                assertThat(jdbc.queryForMap("select * from order_item_options where id=?",snapshot)).isEqualTo(before);
            }
        });
        fresh(ds->{assertThat(migrate(ds,null)).isEqualTo(38);assertThat(migrate(ds,null)).isZero();});
    }

    @Test void sharedValuesCategoryOverridesNewItemsAndDeactivateCycleAreStorePrivateAndReadOnly() throws Exception {
        fresh(ds->{migrate(ds,null);JdbcTemplate jdbc=new JdbcTemplate(ds);Fixture f=fixture(jdbc);
            try(Harness h=new Harness(ds)) {
                var master=h.service.create(write(f.store(),"remove_cilantro","走香菜",true,10));
                long beforeRevision=revision(jdbc,f.store());
                StoreRemoveService.ApplicabilityRequest rules=rules(List.of(f.category()),rule(f.item(),"DENY"));
                h.service.setApplicability(master.id(),rules);
                assertThat(h.service.getApplicability(master.id()).items()).filteredOn(i->i.item_id().equals(f.item())).allMatch(i->!i.effective());
                assertThat(jdbc.queryForObject("select count(*) from menu_item_options where menu_item_id=?",Integer.class,f.item())).isZero();
                long newItem=id(jdbc,"insert into menu_items(store_id,category_id,name_zh,sort_order) values (?,?,'新菜',0) returning id",f.store(),f.category());
                h.service.refreshStoreAssignments(f.store());
                long option=jdbc.queryForObject("select id from menu_item_options where menu_item_id=?",Long.class,newItem);
                assertThat(h.service.getRemoves(f.store()).removes().get(0).item_ids()).containsExactly(newItem);
                h.service.update(master.id(),write(f.store(),"remove_cilantro","免香菜",false,20));
                assertThat(jdbc.queryForObject("select is_active from menu_item_options where id=?",Boolean.class,option)).isFalse();
                h.service.update(master.id(),write(f.store(),"remove_cilantro","免香菜",true,20));
                assertThat(jdbc.queryForMap("select id,name_zh,price_delta,is_active,sort_order from menu_item_options where id=?",option))
                    .containsEntry("name_zh","免香菜").containsEntry("price_delta",new BigDecimal("0.00")).containsEntry("is_active",true).containsEntry("sort_order",20);
                MenuItemOption raw = new MenuItemOption();raw.id=option;raw.menu_item_id=newItem;raw.store_remove_id=master.id();raw.store_remove_store_id=f.store();raw.name_zh="错误副本";raw.is_active=false;
                var projection = new StoreRemoveOptionResolver(new NamedParameterJdbcTemplate(ds)).resolve(List.of(raw)).get(0);
                assertThat(projection.name_zh).isEqualTo("免香菜");assertThat(projection.is_active).isTrue();assertThat(projection.price_delta).isEqualByComparingTo(BigDecimal.ZERO);
                assertThat(raw.name_zh).isEqualTo("错误副本");assertThat(raw.is_active).isFalse();
                long stable=revision(jdbc,f.store());
                h.service.getRemoves(f.store());h.service.getApplicability(master.id());h.service.reconcile(f.store(),true);
                assertThat(revision(jdbc,f.store())).isEqualTo(stable).isGreaterThan(beforeRevision);
                assertThat(jdbc.queryForObject("select count(*) from store_removes where store_id=?",Integer.class,f.otherStore())).isZero();
                assertThatThrownBy(()->h.service.setApplicability(master.id(),rules(List.of(f.otherCategory())))).hasMessageContaining("REMOVE_STORE_MISMATCH");
                assertThat(revision(jdbc,f.store())).isEqualTo(stable);
                // Move out of an inherited category; existing ID remains but no longer offered.
                jdbc.update("update menu_items set category_id=null where id=?",newItem);h.service.refreshStoreAssignments(f.store());
                assertThat(jdbc.queryForObject("select is_active from menu_item_options where id=?",Boolean.class,option)).isFalse();
                rules=rules(List.of(),rule(newItem,"ALLOW"));h.service.setApplicability(master.id(),rules);
                assertThat(jdbc.queryForObject("select id from menu_item_options where menu_item_id=?",Long.class,newItem)).isEqualTo(option);
                assertThat(jdbc.queryForObject("select is_active from menu_item_options where id=?",Boolean.class,option)).isTrue();
            }
        });
    }

    @Test void reconciliationRetainsNameParentAndMoneyConflictsAndIsIdempotentAndAtomic() throws Exception {
        fresh(ds->{migrate(ds,null);JdbcTemplate jdbc=new JdbcTemplate(ds);Fixture f=fixture(jdbc);
            long otherSameStore=id(jdbc,"insert into menu_items(store_id,category_id,name_zh,sort_order) values (?,?,'第二菜',0) returning id",f.store(),f.category());
            legacy(jdbc,f.item(),"remove_carrot","走胡萝卜",0,true);legacy(jdbc,otherSameStore,"remove_carrot","走胡萝卜片",0,true);
            long stableId=legacy(jdbc,f.item(),"remove_green_onion","走葱",0,true);
            legacy(jdbc,otherSameStore,"remove_green_onion","走葱",0,false);
            legacy(jdbc,f.item(),"remove_paid","旧收费",1,true);
            try(Harness h=new Harness(ds)) {
                long revision=revision(jdbc,f.store());
                assertThat(h.service.getRemoves(f.store()).legacy_options()).hasSize(5);
                var dry=h.service.reconcile(f.store(),true);assertThat(dry.linked_options()).isEqualTo(2);assertThat(dry.conflicts()).hasSize(2);
                assertThat(revision(jdbc,f.store())).isEqualTo(revision);
                var apply=h.service.reconcile(f.store(),false);assertThat(apply.linked_options()).isEqualTo(2);
                assertThat(h.service.reconcile(f.store(),false).linked_options()).isZero();
                assertThat(revision(jdbc,f.store())).isEqualTo(revision+1);
                assertThat(jdbc.queryForObject("select store_remove_id from menu_item_options where id=?",Long.class,stableId)).isNotNull();
                assertThat(h.service.getRemoves(f.store()).legacy_options()).hasSize(3);
                var master=h.service.getRemoves(f.store()).removes().get(0);
                assertThat(master.item_ids()).containsExactly(f.item());
                // A late propagation failure must roll back the master and revision.
                jdbc.execute("create function fail_remove_update() returns trigger language plpgsql as $$ begin raise exception 'TEST_LATE_PROPAGATION_FAILURE'; end $$");
                jdbc.execute("create trigger fail_remove_update before update on menu_item_options for each row execute function fail_remove_update()");
                assertThatThrownBy(()->h.service.update(master.id(),write(f.store(),master.code(),"新名",true,0))).hasMessageContaining("TEST_LATE_PROPAGATION_FAILURE");
                assertThat(h.service.getRemoves(f.store()).removes().get(0).name_zh()).isEqualTo("走葱");
                assertThat(revision(jdbc,f.store())).isEqualTo(revision+1);
            }
        });
    }

    @Test void selectionRejectsForeignParentWrongSideUnavailableAssignmentAndIngredientConflicts() throws Exception {
        fresh(ds->{migrate(ds,null);JdbcTemplate jdbc=new JdbcTemplate(ds);Fixture f=fixture(jdbc);
            long side=id(jdbc,"insert into menu_items(store_id,category_id,sku,name_zh,sort_order,is_active) values (?,?,'cucumber_salad','黄瓜',0,true) returning id",f.store(),f.category());
            long sideRemove=legacy(jdbc,side,"remove_peanut","走花生",0,true);
            long foreign=legacy(jdbc,f.otherItem(),"remove_peanut","走花生",0,true);
            long mainRemove=legacy(jdbc,f.item(),"remove_cilantro","走香菜",0,true);
            long addon=id(jdbc,"insert into menu_item_options(menu_item_id,option_type,option_group,option_code,name_zh,price_delta,is_active) values (?,'addon','ADD_ON','cilantro','加香菜',0,true) returning id",f.item());
            var repo=mock(MenuItemOptionRepository.class);when(repo.findById(anyLong())).thenAnswer(inv->{
                Long id=inv.getArgument(0);return jdbc.query("select * from menu_item_options where id=?",(rs,n)->{
                    MenuItemOption o=new MenuItemOption();o.id=rs.getLong("id");o.menu_item_id=rs.getLong("menu_item_id");o.option_type=rs.getString("option_type");o.option_group=rs.getString("option_group");o.option_code=rs.getString("option_code");o.price_delta=rs.getBigDecimal("price_delta");o.is_active=rs.getBoolean("is_active");o.store_remove_id=rs.getObject("store_remove_id",Long.class);o.store_remove_store_id=rs.getObject("store_remove_store_id",Long.class);o.parent_option_id=rs.getObject("parent_option_id",Long.class);return o;
                },id).stream().findFirst();
            });
            var validator=new RemoveSelectionValidator(jdbc,repo,new StoreRemoveOptionResolver(new NamedParameterJdbcTemplate(ds)),mock(StoreComboConfigurationService.class));
            // Ordinary offline lines without Remove must keep the missing-menu snapshot contract.
            validator.validateRequest(f.store(),999999L,List.of(selection(-900L,"size","SIZE","size_large",null)));
            var combo=selection(-20000L,"addon","COMBO","combo",null);
            var parent=selection(-20203L,"addon","COMBO_SIDE","combo_cucumber_salad",null);
            validator.validateRequest(f.store(),f.item(),List.of(combo,parent,selection(sideRemove,"remove","COMBO_SIDE_REMOVE","remove_peanut",parent.option_id)));
            assertThatThrownBy(()->validator.validateRequest(f.store(),f.item(),List.of(combo,selection(sideRemove,"remove","REMOVE","remove_peanut",999L)))).hasMessageContaining("REMOVE_COMBO_SIDE_REQUIRED");
            assertThatThrownBy(()->validator.validateRequest(f.store(),f.item(),List.of(combo,parent,selection(foreign,"remove","COMBO_SIDE_REMOVE","remove_peanut",parent.option_id)))).hasMessageContaining("REMOVE_STORE_MISMATCH");
            parent.option_code_snapshot="combo_edamame";
            assertThatThrownBy(()->validator.validateRequest(f.store(),f.item(),List.of(combo,parent,selection(sideRemove,"remove","COMBO_SIDE_REMOVE","remove_peanut",parent.option_id)))).hasMessageContaining("REMOVE_COMBO_SIDE_INVALID");
            assertThatThrownBy(()->validator.validateRequest(f.store(),f.item(),List.of(selection(mainRemove,"remove","REMOVE","remove_cilantro",null),selection(addon,"addon","ADD_ON","cilantro",null)))).hasMessageContaining("ADD_REMOVE_CONFLICT");
            jdbc.update("update menu_item_options set is_active=false where id=?",mainRemove);
            assertThatThrownBy(()->validator.validateRequest(f.store(),f.item(),List.of(selection(mainRemove,"remove","REMOVE","remove_cilantro",null)))).hasMessageContaining("REMOVE_ASSIGNMENT_UNAVAILABLE");
            try (Harness h = new Harness(ds)) {
                h.service.reconcile(f.store(),false);
                long remove=h.service.getRemoves(f.store()).removes().stream().filter(r->r.code().equals("remove_peanut")).findFirst().orElseThrow().id();
                h.service.setApplicability(remove,rules(List.of(),rule(side,"DENY")));
                parent.option_code_snapshot="combo_cucumber_salad";
                assertThatThrownBy(()->validator.validateRequest(f.store(),f.item(),List.of(combo,parent,selection(sideRemove,"remove","COMBO_SIDE_REMOVE","remove_peanut",parent.option_id)))).hasMessageContaining("REMOVE_ASSIGNMENT_UNAVAILABLE");
            }
        });
    }

    @Test void duplicateSameItemIdentitiesRemainUnlinkedEvenWhenAllValuesAgree() throws Exception {
        fresh(ds->{migrate(ds,null);JdbcTemplate jdbc=new JdbcTemplate(ds);Fixture f=fixture(jdbc);
            long first=legacy(jdbc,f.item(),"remove_cilantro","走香菜",0,true);
            long duplicate=legacy(jdbc,f.item(),"remove_cilantro","走香菜",0,true);
            try(Harness h=new Harness(ds)) {
                long before=revision(jdbc,f.store());
                assertThat(h.service.reconcile(f.store(),true).conflicts()).singleElement().satisfies(c->{
                    assertThat(c.reason()).isEqualTo("REMOVE_RELATIONSHIP_CONFLICT");assertThat(c.option_ids()).containsExactly(first,duplicate);
                });
                assertThat(h.service.reconcile(f.store(),false).linked_options()).isZero();
                assertThat(h.service.getRemoves(f.store()).legacy_options()).hasSize(2);
                assertThat(h.service.getRemoves(f.store()).removes()).isEmpty();
                assertThat(revision(jdbc,f.store())).isEqualTo(before);
            }
        });
    }

    @Test void configuredSideBindingsOverrideLegacySkuAndAllowDynamicCodesWithoutAllowingOldSide() throws Exception {
        fresh(ds->{migrate(ds,null);JdbcTemplate jdbc=new JdbcTemplate(ds);Fixture f=fixture(jdbc);
            long oldSide=id(jdbc,"insert into menu_items(store_id,sku,name_zh,sort_order,is_active) values (?,'cucumber_salad','旧配菜',0,true) returning id",f.store());
            long boundSide=id(jdbc,"insert into menu_items(store_id,sku,name_zh,sort_order,is_active) values (?,'private_daily_side','新配菜',0,true) returning id",f.store());
            long oldRemove=legacy(jdbc,oldSide,"remove_peanut","旧走花生",0,true);
            long boundRemove=legacy(jdbc,boundSide,"remove_peanut","新走花生",0,true);
            long group=id(jdbc,"insert into store_combo_groups(store_id,group_code,name_zh,name_en,selection_rule,required,enabled,display_order,created_at,updated_at) values (?,'COMBO_SIDE','小菜','Side','EXACTLY_ONE',true,true,0,current_timestamp,current_timestamp) returning id",f.store());
            long canonical=component(jdbc,f.store(),group,"combo_cucumber_salad",boundSide);
            long dynamic=component(jdbc,f.store(),group,"combo_private_daily_side",boundSide);
            var validator=validator(jdbc,ds);
            var combo=selection(-20000L,"addon","COMBO","combo",null);
            var parent=selection(-20203L,"addon","COMBO_SIDE","combo_cucumber_salad",null);
            // Parent can be omitted: both validation and save inference use the Store binding.
            validator.validateRequest(f.store(),f.item(),List.of(combo,parent,selection(boundRemove,"remove","REMOVE","remove_peanut",null)));
            assertThatThrownBy(()->validator.validateRequest(f.store(),f.item(),List.of(combo,parent,selection(oldRemove,"remove","REMOVE","remove_peanut",parent.option_id)))).hasMessageContaining("REMOVE_COMBO_SIDE_INVALID");
            parent.option_code_snapshot="combo_private_daily_side";
            validator.validateRequest(f.store(),f.item(),List.of(combo,parent,selection(boundRemove,"remove","REMOVE","remove_peanut",parent.option_id)));
            assertThatThrownBy(()->validator.validateRequest(f.store(),f.item(),List.of(combo,parent,selection(oldRemove,"remove","REMOVE","remove_peanut",parent.option_id)))).hasMessageContaining("REMOVE_COMBO_SIDE_INVALID");
            jdbc.update("update store_combo_components set enabled=false where id=?",dynamic);
            assertThatThrownBy(()->validator.validateRequest(f.store(),f.item(),List.of(combo,parent,selection(boundRemove,"remove","REMOVE","remove_peanut",parent.option_id)))).hasMessageContaining("REMOVE_COMBO_SIDE_INVALID");
            parent.option_code_snapshot="combo_cucumber_salad";
            jdbc.update("update store_combo_components set linked_menu_item_id=? where id=?",f.otherItem(),canonical);
            assertThatThrownBy(()->validator.validateRequest(f.store(),f.item(),List.of(combo,parent,selection(boundRemove,"remove","REMOVE","remove_peanut",parent.option_id)))).hasMessageContaining("REMOVE_COMBO_SIDE_INVALID");
        });
    }

    @Test void deletingEmptyCategoryOrUnreferencedDeniedItemOnlyCascadesApplicabilityMapping() throws Exception {
        fresh(ds->{migrate(ds,null);JdbcTemplate jdbc=new JdbcTemplate(ds);Fixture f=fixture(jdbc);
            long empty=id(jdbc,"insert into menu_categories(store_id,name_zh,sort_order) values (?,'空分类',0) returning id",f.store());
            long denied=id(jdbc,"insert into menu_items(store_id,name_zh,sort_order) values (?,'仅DENY无选项',0) returning id",f.store());
            try(Harness h=new Harness(ds)) {
                var master=h.service.create(write(f.store(),"remove_cilantro","走香菜",true,0));
                var other=h.service.create(write(f.otherStore(),"remove_cilantro","其他店走香菜",true,0));
                h.service.setApplicability(master.id(),rules(List.of(empty),rule(denied,"DENY")));
                h.service.setApplicability(other.id(),rules(List.of(f.otherCategory())));
                assertThat(jdbc.queryForObject("select count(*) from menu_item_options where menu_item_id=?",Integer.class,denied)).isZero();
                jdbc.update("delete from menu_categories where id=?",empty);
                jdbc.update("delete from menu_items where id=?",denied);
                assertThat(h.service.getApplicability(master.id()).category_ids()).isEmpty();
                assertThat(jdbc.queryForObject("select count(*) from store_remove_item_rules where menu_item_id=?",Integer.class,denied)).isZero();
                assertThat(h.service.getRemoves(f.store()).removes()).extracting(StoreRemoveService.Remove::id).containsExactly(master.id());
                assertThat(h.service.getApplicability(other.id()).category_ids()).containsExactly(f.otherCategory());
            }
        });
    }

    private static long component(JdbcTemplate jdbc,Long store,Long group,String code,Long item) {
        return id(jdbc,"insert into store_combo_components(store_id,group_id,component_group,component_code,name_zh,name_en,enabled,display_order,linked_menu_item_id,business_behavior,created_at,updated_at) values (?,?,'COMBO_SIDE',?,'配菜','Side',true,0,?,'LINKED_MENU_ITEM',current_timestamp,current_timestamp) returning id",store,group,code,item);
    }

    private static RemoveSelectionValidator validator(JdbcTemplate jdbc,DriverManagerDataSource ds) {
        var repository=mock(MenuItemOptionRepository.class);
        when(repository.findById(anyLong())).thenAnswer(inv->{
            Long id=inv.getArgument(0);
            return jdbc.query("select * from menu_item_options where id=?",(rs,n)->{
                MenuItemOption option=new MenuItemOption();option.id=rs.getLong("id");option.menu_item_id=rs.getLong("menu_item_id");
                option.option_type=rs.getString("option_type");option.option_group=rs.getString("option_group");option.option_code=rs.getString("option_code");
                option.price_delta=rs.getBigDecimal("price_delta");option.is_active=rs.getBoolean("is_active");
                option.store_remove_id=rs.getObject("store_remove_id",Long.class);option.store_remove_store_id=rs.getObject("store_remove_store_id",Long.class);
                option.parent_option_id=rs.getObject("parent_option_id",Long.class);return option;
            },id).stream().findFirst();
        });
        return new RemoveSelectionValidator(jdbc,repository,new StoreRemoveOptionResolver(new NamedParameterJdbcTemplate(ds)),mock(StoreComboConfigurationService.class));
    }

    @Test void genericReconcileRetainsUnmaterializableMetadataAndMixedPrefixIdentityWithoutWriting() throws Exception {
        fresh(ds->{migrate(ds,null);JdbcTemplate jdbc=new JdbcTemplate(ds);Fixture f=fixture(jdbc);
            long second=id(jdbc,"insert into menu_items(store_id,name_zh,sort_order) values (?,'second',0) returning id",f.store());
            long missingGroup=id(jdbc,"insert into menu_item_options(menu_item_id,option_type,option_code,name_zh,price_delta,is_active) values (?,'remove','remove_radish','走萝卜',0,true) returning id",f.item());
            legacy(jdbc,f.item(),"cilantro","走香菜",0,true);
            legacy(jdbc,second,"remove_cilantro","走香菜",0,true);
            var before=jdbc.queryForList("select * from menu_item_options order by id");
            try(Harness h=new Harness(ds)) {
                long revision=revision(jdbc,f.store());
                var dry=h.service.reconcile(f.store(),true);
                assertThat(dry.linked_options()).isZero();
                assertThat(dry.conflicts()).anyMatch(c->"REMOVE_VALUES_INVALID".equals(c.reason()) && c.option_ids().contains(missingGroup));
                assertThat(dry.conflicts()).anyMatch(c->"REMOVE_CODE_ALIAS_CONFLICT".equals(c.reason()));
                assertThat(h.service.reconcile(f.store(),false).linked_options()).isZero();
                assertThat(jdbc.queryForList("select * from menu_item_options order by id")).isEqualTo(before);
                assertThat(jdbc.queryForObject("select count(*) from store_removes",Integer.class)).isZero();
                assertThat(revision(jdbc,f.store())).isEqualTo(revision);
            }
        });
    }

    private static StoreRemoveService.WriteRequest write(Long store,String code,String zh,boolean active,int sort) {
        var r=new StoreRemoveService.WriteRequest();r.store_id=store;r.code=code;r.name_zh=zh;r.name_en="";r.active=active;r.sort_order=sort;return r;
    }
    private static StoreRemoveService.RuleRequest rule(Long item,String mode) {var r=new StoreRemoveService.RuleRequest();r.item_id=item;r.mode=mode;return r;}
    private static StoreRemoveService.ApplicabilityRequest rules(List<Long> categories,StoreRemoveService.RuleRequest...items) {var r=new StoreRemoveService.ApplicabilityRequest();r.category_ids=categories;r.items=List.of(items);return r;}
    private static CreateOrderItemOptionRequest selection(Long id,String type,String group,String code,Long parent) {var r=new CreateOrderItemOptionRequest();r.option_id=id;r.option_type_snapshot=type;r.option_group_snapshot=group;r.option_code_snapshot=code;r.parent_option_id_snapshot=parent;r.option_price_snapshot=BigDecimal.ZERO;r.quantity=1;return r;}
    private record Fixture(Long store,Long category,Long item,Long otherStore,Long otherCategory,Long otherItem) {}
    private static Fixture fixture(JdbcTemplate jdbc) {
        long org=id(jdbc,"insert into organizations(name,code) values ('Test','remove_test') returning id");
        long store=id(jdbc,"insert into stores(name,code,organization_id) values ('Test','remove_test',?) returning id",org);
        long other=id(jdbc,"insert into stores(name,code,organization_id) values ('Other','remove_other',?) returning id",org);
        long category=id(jdbc,"insert into menu_categories(store_id,name_zh,sort_order) values (?,'类',0) returning id",store);
        long otherCategory=id(jdbc,"insert into menu_categories(store_id,name_zh,sort_order) values (?,'别类',0) returning id",other);
        long item=id(jdbc,"insert into menu_items(store_id,category_id,name_zh,sort_order) values (?,?,'菜',0) returning id",store,category);
        long otherItem=id(jdbc,"insert into menu_items(store_id,category_id,name_zh,sort_order) values (?,?,'别菜',0) returning id",other,otherCategory);
        return new Fixture(store,category,item,other,otherCategory,otherItem);
    }
    private static long legacy(JdbcTemplate jdbc,long item,String code,String zh,int price,boolean active) {return id(jdbc,"insert into menu_item_options(menu_item_id,option_type,option_group,option_code,name_zh,price_delta,is_active) values (?,'remove','REMOVE',?,?,?,?) returning id",item,code,zh,price,active);}
    private static long revision(JdbcTemplate jdbc,Long store) {return jdbc.queryForObject("select menu_revision from stores where id=?",Long.class,store);}
    private static long id(JdbcTemplate jdbc,String sql,Object...args) {return jdbc.queryForObject(sql,Long.class,args);}
    private static int migrate(DriverManagerDataSource ds,String target) {var c=Flyway.configure().dataSource(ds).locations("classpath:db/migration");if(target!=null)c.target(target);return c.load().migrate().migrationsExecuted;}
    private static class Harness implements AutoCloseable {
        final AnnotationConfigApplicationContext context=new AnnotationConfigApplicationContext();final StoreRemoveService service;
        Harness(DriverManagerDataSource ds) {
            JdbcTemplate jdbc=new JdbcTemplate(ds);MenuRevisionService revision=mock(MenuRevisionService.class);
            when(revision.lockStoresInOrder(any())).thenAnswer(inv->{jdbc.queryForList("select id from stores where id in (select id from stores) order by id for update");return List.of();});
            doAnswer(inv->{jdbc.update("update stores set menu_revision=menu_revision+1 where id=?",inv.getArgument(0,Long.class));return null;}).when(revision).incrementRevision(anyLong());
            context.register(Transactions.class);context.registerBean("transactionManager",DataSourceTransactionManager.class,()->new DataSourceTransactionManager(ds));
            context.registerBean(StoreRemoveService.class,()->new StoreRemoveService(jdbc,revision,mock(EntityManager.class)));context.refresh();service=context.getBean(StoreRemoveService.class);
        }
        public void close(){context.close();}
    }
    private static void fresh(DatabaseTest test) throws Exception {
        String url=System.getenv("REMOVE_TEST_POSTGRES_URL"),user=System.getenv().getOrDefault("REMOVE_TEST_POSTGRES_USER",System.getProperty("user.name")),password=System.getenv().getOrDefault("REMOVE_TEST_POSTGRES_PASSWORD","");
        String name="remove_test_"+UUID.randomUUID().toString().replace("-","");
        try(Connection admin=DriverManager.getConnection(url,user,password)) {
            try(var s=admin.createStatement()){s.execute("create database "+name);}
            try {test.run(new DriverManagerDataSource(url.substring(0,url.lastIndexOf('/')+1)+name,user,password));}
            finally {try(var s=admin.createStatement()){s.execute("drop database "+name);}}
        }
    }
    @FunctionalInterface private interface DatabaseTest {void run(DriverManagerDataSource ds) throws Exception;}
}
