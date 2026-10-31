package com.restaurant.system.template.bootstrap;

import com.restaurant.system.auth.service.PasswordService;
import com.restaurant.system.menu.template.TemplateMenuModels.Group;
import com.restaurant.system.menu.template.TemplateMenuModels.Groups;
import com.restaurant.system.menu.template.TemplateMenuModels.Option;
import com.restaurant.system.menu.template.TemplateMenuService;
import com.restaurant.system.modules.ModuleContractLoader;
import com.restaurant.system.printing.routing.TemplateRoutingModels;
import com.restaurant.system.printing.routing.TemplateRoutingService;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Seeds only the separately authorized, isolated Template Test database. */
@Component
@Profile("template-test")
@ConditionalOnProperty(name = "app.template.test-bootstrap.enabled", havingValue = "true")
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TemplateTestBootstrap implements ApplicationRunner {

    private static final Logger logger = LoggerFactory.getLogger(TemplateTestBootstrap.class);
    private static final String DATABASE = "restaurant_template_test";
    private static final String ORG = "SYNTHETIC_TEMPLATE_TEST_ORG";
    private static final String OTHER_ORG = "SYNTHETIC_TEMPLATE_TEST_OTHER_ORG";
    private static final String STORE = "SYNTHETIC_TEMPLATE_TEST_MAIN";
    private static final String OTHER_STORE = "SYNTHETIC_TEMPLATE_TEST_OTHER";
    private final JdbcTemplate jdbc;
    private final Environment environment;
    private final PasswordService passwords;
    private final TemplateMenuService menus;
    private final TemplateRoutingService routing;

    public TemplateTestBootstrap(JdbcTemplate jdbc, Environment environment, PasswordService passwords,
                                 TemplateMenuService menus, TemplateRoutingService routing) {
        this.jdbc = jdbc;
        this.environment = environment;
        this.passwords = passwords;
        this.menus = menus;
        this.routing = routing;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments arguments) {
        requireEnvironment();
        String ownerPassword = secret("TEMPLATE_TEST_OWNER_PASSWORD");
        String managerPassword = secret("TEMPLATE_TEST_MANAGER_PASSWORD");
        if (ownerPassword.equals(managerPassword)) {
            throw blocked("Owner and Manager require distinct environment passwords");
        }
        jdbc.execute("select pg_advisory_xact_lock(2026103101)");
        requireSyntheticDatabase();

        long ownerRole = role("OWNER", "Owner");
        long managerRole = role("MANAGER", "Manager");
        long organization = organization(ORG, "SYNTHETIC Template Test");
        long otherOrganization = organization(OTHER_ORG, "SYNTHETIC Scope Negative");
        long store = store(organization, STORE, "SYNTHETIC Template Restaurant");
        long otherStore = store(otherOrganization, OTHER_STORE, "SYNTHETIC Other Organization Store");
        modules(store);
        modules(otherStore);
        long owner = login(store, ownerRole, "template_owner", "SYNTHETIC Template Owner", ownerPassword);
        long manager = login(store, managerRole, "template_manager", "SYNTHETIC Template Manager", managerPassword);
        membership(organization, store, owner, ownerRole, "OWNER", true);
        membership(organization, store, manager, managerRole, "MANAGER", false);
        requireNoForeignMembership(owner, manager, organization, store);

        long hot = station(store, "WOK", "SYNTHETIC Hot Kitchen", 0);
        long fried = station(store, "DEEPFRIED", "SYNTHETIC Fried Kitchen", 1);
        long cold = station(store, "COLD", "SYNTHETIC Cold Kitchen", 2);
        long hotCategory = category(store, "SYNTHETIC_HOT", "SYNTHETIC 热厨", 0);
        long friedCategory = category(store, "SYNTHETIC_FRIED", "SYNTHETIC 炸物", 1);
        long coldCategory = category(store, "SYNTHETIC_COLD", "SYNTHETIC 冷菜", 2);
        pricing(store);
        if (count("select count(*) from template_store_configuration where store_id=?", store) == 0) {
            menus.saveLibrary(store, library());
        }
        Groups library = menus.library(store);
        SeedItem springRoll = item(store, friedCategory, fried, "synthetic_spring_roll", "炸春卷", "SYNTHETIC Spring Roll", "4.00");
        SeedItem chicken = item(store, hotCategory, hot, "synthetic_chicken", "炒鸡", "SYNTHETIC Chicken", "12.00");
        SeedItem noodle = item(store, hotCategory, hot, "synthetic_beef_noodle", "牛肉面", "SYNTHETIC Beef Noodle", "13.00");
        SeedItem side = item(store, coldCategory, cold, "synthetic_cucumber_salad", "SYNTHETIC 拌黄瓜", "SYNTHETIC Cucumber Salad", "3.00");
        combo(store, side.id());
        if (springRoll.created()) menus.saveItem(store, springRoll.id(), new Groups());
        if (chicken.created()) menus.saveItem(store, chicken.id(), copyGroups(library, false));
        if (noodle.created()) menus.saveItem(store, noodle.id(), copyGroups(library, true));
        if (side.created()) menus.saveItem(store, side.id(), new Groups());

        long printerA = printer(store, "A");
        long printerB = printer(store, "B");
        long printerC = printer(store, "C");
        assignment(store, "GRAB", printerA);
        assignment(store, "HOT_KITCHEN", printerA);
        assignment(store, "FRONTDESK_RECEIPT", printerA);
        if (count("select count(*) from dining_tables where store_id=? and table_code='SYN-T01'", store) == 0) {
            jdbc.update("""
                insert into dining_tables(store_id,table_code,table_name,area_name,capacity,supports_split,
                  split_label_mode,sort_order,is_active,created_at,updated_at)
                values(?,'SYN-T01','SYNTHETIC Table 1','SYNTHETIC',4,true,'LEFT_RIGHT',0,true,current_timestamp,current_timestamp)
                """, store);
        }
        if (routing.configuration(store).revision == 0) {
            TemplateRoutingModels.Configuration configuration = new TemplateRoutingModels.Configuration();
            configuration.enabled = true;
            configuration.groups.add(route("A", "SYNTHETIC 热厨", 0, hotCategory, printerA));
            configuration.groups.add(route("B", "SYNTHETIC 炸物", 1, friedCategory, printerB));
            configuration.groups.add(route("C", "SYNTHETIC 冷菜", 2, coldCategory, printerC));
            routing.save(store, configuration);
        }
        long otherStation = station(otherStore, "WOK", "SYNTHETIC Scope Negative Kitchen", 0);
        long otherCategory = category(otherStore, "SYNTHETIC_OTHER", "SYNTHETIC Other Menu", 0);
        SeedItem otherItem = item(otherStore, otherCategory, otherStation, "synthetic_scope_negative", "SYNTHETIC Scope Negative Item", "SYNTHETIC Scope Negative Item", "1.00");
        if (otherItem.created()) menus.saveItem(otherStore, otherItem.id(), new Groups());
        requireSyntheticDatabase();
        logger.info("Independent SYNTHETIC Template Test initialized: store {}, scope-negative store {}. Credentials are not logged.", store, otherStore);
    }

    private void requireEnvironment() {
        if (!Arrays.asList(environment.getActiveProfiles()).contains("template-test")
            || !"test".equals(environment.getProperty("app.environment"))
            || !environment.getProperty("app.template.test-bootstrap.enabled", Boolean.class, false)
            || !environment.getProperty("app.template.enabled", Boolean.class, false)) {
            throw blocked("Explicit template-test profile and Test configuration are required");
        }
        for (String flag : List.of("runtime-enabled", "safe-metadata-enabled", "default-users-enabled",
            "demo-data-enabled", "membership-supplement-enabled", "production-bootstrap-enabled")) {
            if (environment.getProperty("app.seed." + flag, Boolean.class, true)) {
                throw blocked("All ordinary RuntimeDataSeeder switches must be explicitly disabled");
            }
        }
        if (environment.getProperty("app.seed.force-overwrite", Boolean.class, false)
            || environment.getProperty("UBER_EATS_ENABLED", Boolean.class, false)
            || environment.getProperty("UBER_EATS_WORKER_ENABLED", Boolean.class, true)) {
            throw blocked("Overwrite and external order processing must be disabled");
        }
        String expected = environment.getProperty("TEMPLATE_TEST_DATABASE_NAME", DATABASE);
        String actual = jdbc.queryForObject("select current_database()", String.class);
        if (!DATABASE.equals(expected) || !DATABASE.equals(actual)) {
            throw blocked("The database must be the isolated restaurant_template_test database");
        }
    }

    private String secret(String name) {
        String value = environment.getProperty(name);
        if (value == null || value.isBlank() || value.length() < 16) {
            throw blocked(name + " must contain a newly generated Test-only password of at least 16 characters");
        }
        return value;
    }

    private void requireSyntheticDatabase() {
        reject("select count(*) from organizations where code is null or code not in (?,?) or name is null or name not like 'SYNTHETIC%'", ORG, OTHER_ORG);
        reject("""
            select count(*) from stores s left join organizations o on o.id=s.organization_id
            where s.code is null or s.code not in (?,?) or o.code is null or o.code not in (?,?)
              or s.name is null or s.name not like 'SYNTHETIC%' or s.printing_mode is distinct from 'MOCK'
            """, STORE, OTHER_STORE, ORG, OTHER_ORG);
        List<String> storeTables = jdbc.queryForList("""
            select table_name from information_schema.columns
            where table_schema='public' and column_name='store_id' order by table_name
            """, String.class);
        for (String table : storeTables) {
            String nullableAudit = "audit_logs".equals(table) ? "t.store_id is not null and " : "";
            reject("select count(*) from " + identifier(table) + " t where " + nullableAudit
                + "not exists(select 1 from stores s where s.id=t.store_id and s.code in (?,?))", STORE, OTHER_STORE);
        }
        for (String table : jdbc.queryForList("""
            select table_name from information_schema.columns
            where table_schema='public' and column_name='organization_id' and table_name<>'organizations'
            order by table_name
            """, String.class)) {
            reject("select count(*) from " + identifier(table) + " t where t.organization_id is not null and "
                + "not exists(select 1 from organizations o where o.id=t.organization_id and o.code in (?,?))", ORG, OTHER_ORG);
        }
        reject("select count(*) from store_devices");
        reject("select count(*) from printer_configs where nullif(btrim(ip_address),'') is not null or port is not null or printer_type is distinct from 'MOCK'");
        for (String table : jdbc.queryForList("select tablename from pg_tables where schemaname='public' and tablename like 'uber_eats_%'", String.class)) {
            reject("select count(*) from " + identifier(table));
        }
        reject("select count(*) from users u where not exists(select 1 from stores s where s.id=u.store_id and s.code in (?,?))", STORE, OTHER_STORE);
        reject("select count(*) from user_credentials c where not exists(select 1 from users u where u.id=c.user_id)");
        reject("select count(*) from menu_item_options o where not exists(select 1 from menu_items i where i.id=o.menu_item_id)");
        reject("select count(*) from order_item_options o where not exists(select 1 from order_items i where i.id=o.order_item_id)");
        reject("select count(*) from order_items i where not exists(select 1 from orders o where o.id=i.order_id)");
        reject("select count(*) from print_job_attempts a where not exists(select 1 from print_jobs j where j.id=a.print_job_id)");
        reject("select count(*) from menu_item_bom b where not exists(select 1 from menu_items i where i.id=b.menu_item_id)");
        reject("select count(*) from menu_item_option_bom b where not exists(select 1 from menu_item_options o where o.id=b.menu_item_option_id)");
    }

    private long role(String code, String name) {
        return ensure("select id from roles where code=?", new Object[]{code},
            "insert into roles(code,name,created_at,updated_at) values(?,?,current_timestamp,current_timestamp) returning id", code, name);
    }

    private long organization(String code, String name) {
        return ensure("select id from organizations where code=?", new Object[]{code},
            "insert into organizations(code,name,status,created_at,updated_at) values(?,?,'active',current_timestamp,current_timestamp) returning id", code, name);
    }

    private long store(long org, String code, String name) {
        return ensure("select id from stores where code=? and organization_id=?", new Object[]{code, org}, """
            insert into stores(organization_id,code,name,status,timezone,enable_bar_kitchen_tasks,printing_enabled,
              printing_mode,store_kind,lifecycle_status,provisioning_source,created_at,updated_at)
            values(?,?,?,'active','America/Toronto',false,true,'MOCK','BUSINESS','ACTIVE','LEGACY_EXISTING_STORE',current_timestamp,current_timestamp) returning id
            """, org, code, name);
    }

    private void modules(long store) {
        for (var module : new ModuleContractLoader().loadCatalog().modules()) {
            jdbc.update("""
                insert into store_modules(store_id,module_key,enabled,source,configuration_status,metadata_json,created_at,updated_at)
                values(?,?,?,'SYSTEM_DEFAULT','CONFIGURED','{"synthetic":true,"bootstrap":"template-test-v1"}',current_timestamp,current_timestamp)
                on conflict(store_id,module_key) do nothing
                """, store, module.moduleKey(), module.core());
        }
    }

    private long login(long store, long role, String username, String fullName, String rawPassword) {
        long user = ensure("select id from users where username=?", new Object[]{username}, """
            insert into users(store_id,role_id,username,full_name,status,created_at,updated_at)
            values(?,?,?,?,'active',current_timestamp,current_timestamp) returning id
            """, store, role, username, fullName);
        reject("select count(*) from users where id=? and (store_id<>? or role_id<>? or status<>'active' or full_name not like 'SYNTHETIC%')", user, store, role);
        List<Map<String, Object>> credentials = jdbc.queryForList("select user_id,password_hash,password_algorithm,is_active from user_credentials where lower(login_identifier)=lower(?)", username);
        if (credentials.isEmpty()) {
            jdbc.update("""
                insert into user_credentials(user_id,login_identifier,password_hash,password_algorithm,is_active,
                  password_updated_at,created_at,updated_at)
                values(?,?,?,'BCRYPT',true,current_timestamp,current_timestamp,current_timestamp)
                """, user, username, passwords.hashPassword(rawPassword));
        } else if (credentials.size() != 1 || !Long.valueOf(user).equals(((Number) credentials.get(0).get("user_id")).longValue())
            || !Boolean.TRUE.equals(credentials.get(0).get("is_active"))
            || !"BCRYPT".equals(credentials.get(0).get("password_algorithm"))
            || !passwords.matches(rawPassword, (String) credentials.get(0).get("password_hash"))) {
            throw blocked("Existing synthetic credentials do not match the configured Test identity; no automatic password reset");
        }
        return user;
    }

    private void membership(long org, long store, long user, long role, String roleCode, boolean owner) {
        if (owner) jdbc.update("""
            insert into organization_memberships(organization_id,user_id,role_id,role_code,is_active,created_at,updated_at)
            values(?,?,?,?,true,current_timestamp,current_timestamp) on conflict(user_id,organization_id) do nothing
            """, org, user, role, roleCode);
        jdbc.update("""
            insert into store_memberships(organization_id,store_id,user_id,role_id,role_code,is_active,created_at,updated_at)
            values(?,?,?,?,?,true,current_timestamp,current_timestamp) on conflict(user_id,store_id) do nothing
            """, org, store, user, role, roleCode);
    }

    private void requireNoForeignMembership(long owner, long manager, long org, long store) {
        reject("select count(*) from organization_memberships where user_id in (?,?) and organization_id<>?", owner, manager, org);
        reject("select count(*) from store_memberships where user_id in (?,?) and store_id<>?", owner, manager, store);
    }

    private long station(long store, String code, String name, int order) {
        return ensure("select id from stations where store_id=? and code=?", new Object[]{store, code}, """
            insert into stations(store_id,code,name,name_zh,name_en,station_type,sort_order,is_active,created_at,updated_at)
            values(?,?,?,?,?,'KITCHEN',?,true,current_timestamp,current_timestamp) returning id
            """, store, code, name, name, name, order);
    }

    private long category(long store, String code, String name, int order) {
        return ensure("select id from menu_categories where store_id=? and code=?", new Object[]{store, code}, """
            insert into menu_categories(store_id,code,name_zh,name_en,sort_order,is_active,created_at,updated_at)
            values(?,?,?,?,?,true,current_timestamp,current_timestamp) returning id
            """, store, code, name, name, order);
    }

    private SeedItem item(long store, long category, long station, String sku, String zh, String en, String price) {
        List<Long> existing = jdbc.queryForList("select id from menu_items where store_id=? and sku=?", Long.class, store, sku);
        if (existing.size() > 1) throw blocked("Duplicate synthetic item identity");
        if (!existing.isEmpty()) return new SeedItem(existing.get(0), false);
        long id = jdbc.queryForObject("""
            insert into menu_items(store_id,category_id,station_id,sku,name_zh,name_en,item_type,base_price,
              is_active,is_sold_out,sort_order,created_at,updated_at)
            values(?,?,?,?,?,?,'MAIN',?,true,false,0,current_timestamp,current_timestamp) returning id
            """, Long.class, store, category, station, sku, zh, en, new BigDecimal(price));
        return new SeedItem(id, true);
    }

    private void pricing(long store) {
        jdbc.update("""
            insert into store_pricing_policies(store_id,size_small_delta,size_regular_delta,size_large_delta,
              combo_delta,policy_revision,created_at,updated_at)
            values(?,0,0,2,3,1,current_timestamp,current_timestamp) on conflict(store_id) do nothing
            """, store);
    }

    private void combo(long store, long sideItem) {
        long egg = comboGroup(store, "COMBO_EGG", "SYNTHETIC Egg", "combo_tea_egg", 0);
        long side = comboGroup(store, "COMBO_SIDE", "SYNTHETIC Side", "combo_cucumber_salad", 1);
        jdbc.update("""
            insert into store_combo_components(store_id,group_id,component_group,component_code,name_zh,name_en,
              enabled,display_order,business_behavior,created_at,updated_at)
            values(?,?,'COMBO_EGG','combo_tea_egg','SYNTHETIC 卤蛋','SYNTHETIC Tea Egg',true,0,'NO_KITCHEN_TASK',current_timestamp,current_timestamp)
            on conflict(store_id,component_group,component_code) do nothing
            """, store, egg);
        jdbc.update("""
            insert into store_combo_components(store_id,group_id,component_group,component_code,name_zh,name_en,
              enabled,display_order,linked_menu_item_id,business_behavior,created_at,updated_at)
            values(?,?,'COMBO_SIDE','combo_cucumber_salad','SYNTHETIC 拌黄瓜','SYNTHETIC Cucumber Salad',true,0,?,
              'LEGACY_COMBO_SIDE_TASK',current_timestamp,current_timestamp)
            on conflict(store_id,component_group,component_code) do nothing
            """, store, side, sideItem);
    }

    private long comboGroup(long store, String code, String name, String defaultCode, int order) {
        return ensure("select id from store_combo_groups where store_id=? and group_code=?", new Object[]{store, code}, """
            insert into store_combo_groups(store_id,group_code,name_zh,name_en,selection_rule,required,enabled,
              display_order,default_component_code,created_at,updated_at)
            values(?,?,?,?,'EXACTLY_ONE',true,true,?,?,current_timestamp,current_timestamp) returning id
            """, store, code, name, name, order, defaultCode);
    }

    private long printer(long store, String code) {
        String name = "SYNTHETIC MOCK " + code;
        return ensure("select id from printer_configs where store_id=? and name=?", new Object[]{store, name}, """
            insert into printer_configs(store_id,name,printer_type,text_encoding,paper_width_mm,enabled,created_at,updated_at)
            values(?,?,'MOCK','UTF-8',80,true,current_timestamp,current_timestamp) returning id
            """, store, name);
    }

    private void assignment(long store, String module, long printer) {
        if (count("select count(*) from printer_assignments where store_id=? and module_code=?", store, module) == 0) {
            jdbc.update("""
                insert into printer_assignments(store_id,module_code,printer_id,enabled,takeout_receipt_copies,created_at,updated_at)
                values(?,?,?,true,1,current_timestamp,current_timestamp)
                """, store, module, printer);
        }
    }

    private Groups library() {
        Groups result = new Groups();
        result.groups.add(group("spicy", "SYNTHETIC Spicy Level", "SPICY_LEVEL", true,
            option("spicy_none", "不辣", "0.00", true), option("spicy_mild", "微辣", "0.00", false)));
        result.groups.add(group("addons", "SYNTHETIC Add-on", "ADD_ON", false,
            option("synthetic_extra_cheese", "SYNTHETIC 加芝士", "1.50", false)));
        result.groups.add(group("removes", "SYNTHETIC Remove", "REMOVE", false,
            option("synthetic_no_onion", "SYNTHETIC 不放葱", "0.00", false)));
        result.groups.add(group("size", "SYNTHETIC Size", "SIZE", false));
        result.groups.add(group("combo", "SYNTHETIC Combo", "COMBO", false));
        result.groups.add(group("noodle_shape", "SYNTHETIC 面条粗细", "CUSTOM", true,
            option("synthetic_thin", "SYNTHETIC 二细", "0.00", true), option("synthetic_wide", "SYNTHETIC 大宽", "0.50", false)));
        for (int index = 0; index < result.groups.size(); index++) result.groups.get(index).sort_order = index;
        return result;
    }

    private Groups copyGroups(Groups library, boolean noodle) {
        Groups copy = menus.read(menus.write(library), Groups.class);
        copy.groups.removeIf(g -> !List.of("SPICY_LEVEL", "ADD_ON", "REMOVE").contains(g.kind)
            && !(noodle && List.of("CUSTOM", "COMBO").contains(g.kind)));
        for (Group group : copy.groups) {
            group.source_library_code = group.group_instance_code;
            if ("SPICY_LEVEL".equals(group.kind) && !noodle) {
                group.options.get(0).is_default = false;
                group.options.get(1).is_default = true;
            }
            if ("ADD_ON".equals(group.kind) && noodle) group.options.get(0).price_delta = new BigDecimal("2.00");
        }
        return copy;
    }

    private Group group(String code, String name, String kind, boolean required, Option... options) {
        Group group = new Group();
        group.group_instance_code = code;
        group.name = name;
        group.kind = kind;
        group.required = required;
        group.selection_mode = List.of("ADD_ON", "REMOVE").contains(kind) ? "MULTIPLE" : "SINGLE";
        group.options = new ArrayList<>(List.of(options));
        for (int index = 0; index < group.options.size(); index++) group.options.get(index).sort_order = index;
        return group;
    }

    private Option option(String code, String name, String price, boolean selected) {
        Option option = new Option();
        option.option_code = code;
        option.name_zh = name;
        option.name_en = name;
        option.price_delta = new BigDecimal(price);
        option.is_default = selected;
        return option;
    }

    private TemplateRoutingModels.Group route(String code, String name, int order, long category, long printer) {
        TemplateRoutingModels.Group group = new TemplateRoutingModels.Group();
        group.group_code = code;
        group.name = name;
        group.sort_order = order;
        group.category_ids.add(category);
        group.logical_target = code;
        group.printer_id = printer;
        return group;
    }

    private long ensure(String lookup, Object[] lookupValues, String insert, Object... insertValues) {
        List<Long> existing = jdbc.queryForList(lookup, Long.class, lookupValues);
        if (existing.size() > 1) throw blocked("Duplicate synthetic bootstrap identity");
        return existing.isEmpty() ? jdbc.queryForObject(insert, Long.class, insertValues) : existing.get(0);
    }

    private long count(String sql, Object... values) {
        return jdbc.queryForObject(sql, Long.class, values);
    }

    private void reject(String sql, Object... values) {
        if (count(sql, values) != 0) throw blocked("Non-synthetic, external, or conflicting business data was found; nothing is overwritten");
    }

    private String identifier(String table) {
        return "public.\"" + table.replace("\"", "\"\"") + "\"";
    }

    private IllegalStateException blocked(String message) {
        return new IllegalStateException("TEMPLATE_TEST_BOOTSTRAP_BLOCKED: " + message);
    }

    private record SeedItem(long id, boolean created) {
    }
}
