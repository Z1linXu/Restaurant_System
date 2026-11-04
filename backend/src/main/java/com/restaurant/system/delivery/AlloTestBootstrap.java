package com.restaurant.system.delivery;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurant.system.auth.service.PasswordService;
import com.restaurant.system.modules.ModuleContractLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

/** Only this explicitly isolated Test target, fresh credentials and private merchant source. */
@Component
@Profile("allo-test")
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name="app.delivery.allo-bootstrap-enabled",havingValue="true")
@Order(0)
public class AlloTestBootstrap implements ApplicationRunner {
    private final JdbcTemplate jdbc; private final Environment env; private final PasswordService passwords;
    private final AlloMenuImporter importer; private final StorePresentationService presentation; private final ObjectMapper mapper;
    public AlloTestBootstrap(JdbcTemplate jdbc,Environment env,PasswordService passwords,AlloMenuImporter importer,StorePresentationService presentation,ObjectMapper mapper){this.jdbc=jdbc;this.env=env;this.passwords=passwords;this.importer=importer;this.presentation=presentation;this.mapper=mapper;}
    @Override @Transactional public void run(ApplicationArguments args) throws Exception {
        if(!"test".equals(env.getProperty("app.environment"))||!"restaurant_allo_nouilles_test".equals(jdbc.queryForObject("select current_database()",String.class)))throw new IllegalStateException("Allo bootstrap requires exact isolated Test database");
        for(String flag:List.of("runtime-enabled","safe-metadata-enabled","default-users-enabled","demo-data-enabled","membership-supplement-enabled","production-bootstrap-enabled"))if(env.getProperty("app.seed."+flag,Boolean.class,true))throw new IllegalStateException("Ordinary seeders must be disabled");
        if(env.getProperty("app.template.test-bootstrap.enabled",Boolean.class,true)||env.getProperty("UBER_EATS_ENABLED",Boolean.class,false)||env.getProperty("UBER_EATS_WORKER_ENABLED",Boolean.class,true))throw new IllegalStateException("Other bootstraps and external processors must be disabled");
        String ownerPassword=secret("ALLO_TEST_OWNER_PASSWORD"),managerPassword=secret("ALLO_TEST_MANAGER_PASSWORD");
        if(ownerPassword.equals(managerPassword))throw new IllegalStateException("Distinct Test passwords required");
        Path path=Path.of(env.getRequiredProperty("app.delivery.menu-file"));
        if(!Files.isRegularFile(path)||Files.size(path)>2_000_000)throw new IllegalStateException("Private merchant menu source unavailable or too large");
        var source=mapper.readTree(Files.readAllBytes(path));AlloMenuImporter.validate(source);
        jdbc.execute("select pg_advisory_xact_lock(2026110301)");
        if(jdbc.queryForObject("select count(*) from stores where code not in ('ALLO_NOUILLES_TEST','ALLO_SCOPE_NEGATIVE') or printing_mode not in ('MOCK','DISABLED')",Integer.class)>0)throw new IllegalStateException("Foreign stores cannot be used by this bootstrap");
        long ownerRole=role("OWNER"),managerRole=role("MANAGER");
        long org=organization("ALLO_NOUILLES_TEST_ORG",source.path("merchant").path("display_name").asText());
        long store=store(org,"ALLO_NOUILLES_TEST",source.path("merchant").path("display_name").asText());
        long otherOrg=organization("ALLO_SCOPE_NEGATIVE_ORG","SYNTHETIC scope-negative organization");store(otherOrg,"ALLO_SCOPE_NEGATIVE","SYNTHETIC scope-negative store");
        modules(store);
        long owner=login(store,ownerRole,"allo_owner","TEST Owner",ownerPassword),manager=login(store,managerRole,"allo_manager","TEST Manager",managerPassword);
        membership(org,store,owner,ownerRole,"OWNER",true);membership(org,store,manager,managerRole,"MANAGER",false);
        if(jdbc.queryForObject("select count(*) from store_memberships where user_id in (?,?) and store_id<>?",Integer.class,owner,manager,store)>0)throw new IllegalStateException("Cross-store Test identity rejected");
        jdbc.update("insert into store_pricing_policies(store_id,size_small_delta,size_regular_delta,size_large_delta,combo_delta,policy_revision,created_at,updated_at) values(?,0,0,0,0,1,current_timestamp,current_timestamp) on conflict(store_id) do nothing",store);
        var result=importer.apply(store,source);
        if(jdbc.queryForObject("select count(*) from stores where id=? and presentation_json is not null",Integer.class,store)==0){
            Map<String,Object> view=new LinkedHashMap<>();view.put("display_name",source.path("merchant").path("display_name").asText());view.put("logo_url","/merchant/logo.png");view.put("primary_color",source.path("merchant").path("primary_color").asText("#AF2023"));view.put("default_locale","zh-CN");view.put("reporting_style","GENERIC");view.put("kitchen_configuration","STATION");
            view.put("notice",Map.of("zh-CN","仅堂食：每位顾客需点一份主食；面只续面，饭只续饭，次数不限。请由员工核实资格。","en","Dine-in only: one main per customer. Noodle mains refill noodles; rice mains refill rice. Unlimited refills; staff verifies eligibility.","fr-CA","Sur place seulement : un plat principal par client. Les plats de nouilles donnent droit aux nouilles, les plats de riz au riz. À volonté; admissibilité vérifiée par le personnel."));
            view.put("address",source.path("merchant").path("address").asText(""));view.put("phone",source.path("merchant").path("phone").asText(""));presentation.save(store,view);
            var receipt=new StorePresentationService.ReceiptSettings();receipt.enabled=true;receipt.default_language="zh-CN";receipt.kitchen_station_codes=List.of("BACK_KITCHEN");presentation.saveReceipts(store,receipt);
        }
        long kitchen=printer(store,"MOCK Kitchen"),frontdesk=printer(store,"MOCK Frontdesk");assignment(store,"GRAB",kitchen);assignment(store,"FRONTDESK_RECEIPT",frontdesk);
        for(int i=1;i<=4;i++)if(jdbc.queryForObject("select count(*) from dining_tables where store_id=? and table_code=?",Integer.class,store,"TEST-"+i)==0)jdbc.update("insert into dining_tables(store_id,table_code,table_name,area_name,capacity,supports_split,split_label_mode,sort_order,is_active,created_at,updated_at) values(?,?,?,'TEST',4,false,'LEFT_RIGHT',?,true,current_timestamp,current_timestamp)",store,"TEST-"+i,"TEST "+i,i);
        LoggerFactory.getLogger(getClass()).info("Independent merchant Test initialized: source version {}, created {}, updated {}, unchanged {}, Owner edits preserved {}. No physical transport.",result.menuVersion(),result.created(),result.updated(),result.unchanged(),result.ownerEditedPreserved());
    }
    private String secret(String key){String value=env.getProperty(key);if(value==null||value.length()<24)throw new IllegalStateException("Fresh private Test credentials required");return value;}
    private long ensure(String select,Object[] params,String insert,Object... values){var ids=jdbc.queryForList(select,Long.class,params);if(ids.size()>1)throw new IllegalStateException("Ambiguous Test identity");return ids.isEmpty()?jdbc.queryForObject(insert,Long.class,values):ids.get(0);}
    private long role(String code){return ensure("select id from roles where code=?",new Object[]{code},"insert into roles(code,name,created_at,updated_at) values(?,?,current_timestamp,current_timestamp) returning id",code,code);}
    private long organization(String code,String name){return ensure("select id from organizations where code=?",new Object[]{code},"insert into organizations(code,name,status,created_at,updated_at) values(?,?,'active',current_timestamp,current_timestamp) returning id",code,name);}
    private long store(long org,String code,String name){return ensure("select id from stores where organization_id=? and code=?",new Object[]{org,code},"insert into stores(organization_id,code,name,status,timezone,enable_bar_kitchen_tasks,printing_enabled,printing_mode,store_kind,lifecycle_status,provisioning_source,created_at,updated_at) values(?,?,?,'active','America/Toronto',false,true,'MOCK','BUSINESS','ACTIVE','LEGACY_EXISTING_STORE',current_timestamp,current_timestamp) returning id",org,code,name);}
    private void modules(long store){for(var m:new ModuleContractLoader().loadCatalog().modules())jdbc.update("insert into store_modules(store_id,module_key,enabled,source,configuration_status,metadata_json,created_at,updated_at) values(?,?,?,'SYSTEM_DEFAULT','CONFIGURED','{}',current_timestamp,current_timestamp) on conflict(store_id,module_key) do nothing",store,m.moduleKey(),m.core());}
    private long login(long store,long role,String username,String name,String raw){
        long user=ensure("select id from users where username=?",new Object[]{username},"insert into users(store_id,role_id,username,full_name,status,created_at,updated_at) values(?,?,?,?,'active',current_timestamp,current_timestamp) returning id",store,role,username,name);
        if(jdbc.queryForObject("select count(*) from users where id=? and store_id=? and role_id=? and status='active'",Integer.class,user,store,role)!=1)throw new IllegalStateException("Test identity scope mismatch");
        var rows=jdbc.queryForList("select user_id,password_hash,is_active from user_credentials where lower(login_identifier)=lower(?)",username);
        if(rows.isEmpty())jdbc.update("insert into user_credentials(user_id,login_identifier,password_hash,password_algorithm,is_active,password_updated_at,created_at,updated_at) values(?,?,?,'BCRYPT',true,current_timestamp,current_timestamp,current_timestamp)",user,username,passwords.hashPassword(raw));
        else if(rows.size()!=1||((Number)rows.get(0).get("user_id")).longValue()!=user||!Boolean.TRUE.equals(rows.get(0).get("is_active"))||!passwords.matches(raw,(String)rows.get(0).get("password_hash")))throw new IllegalStateException("Test credentials changed; no automatic reset");return user;
    }
    private void membership(long org,long store,long user,long role,String code,boolean owner){if(owner)jdbc.update("insert into organization_memberships(organization_id,user_id,role_id,role_code,is_active,created_at,updated_at) values(?,?,?,?,true,current_timestamp,current_timestamp) on conflict(user_id,organization_id) do nothing",org,user,role,code);jdbc.update("insert into store_memberships(organization_id,store_id,user_id,role_id,role_code,is_active,created_at,updated_at) values(?,?,?,?,?,true,current_timestamp,current_timestamp) on conflict(user_id,store_id) do nothing",org,store,user,role,code);}
    private long printer(long store,String name){return ensure("select id from printer_configs where store_id=? and name=?",new Object[]{store,name},"insert into printer_configs(store_id,name,printer_type,text_encoding,paper_width_mm,enabled,created_at,updated_at) values(?,?,'MOCK','UTF-8',80,true,current_timestamp,current_timestamp) returning id",store,name);}
    private void assignment(long store,String module,long printer){if(jdbc.queryForObject("select count(*) from printer_assignments where store_id=? and module_code=?",Integer.class,store,module)==0)jdbc.update("insert into printer_assignments(store_id,module_code,printer_id,enabled,takeout_receipt_copies,created_at,updated_at) values(?,?,?,true,1,current_timestamp,current_timestamp)",store,module,printer);}
}
