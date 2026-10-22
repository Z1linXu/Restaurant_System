package com.restaurant.system.menu.remove;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurant.system.common.auth.AuthenticatedUser;
import com.restaurant.system.common.exception.BusinessException;
import com.restaurant.system.menu.service.MenuRevisionService;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Bounded baseline completion/linking. Never reconfigures an item's Remove choices. */
@Service
public class StoreRemoveBaselineReconciler {
    public static class Request {
        public Long store_id;
        public Boolean dry_run;
        public String expected_before_fingerprint;
    }
    public record OptionSnapshot(Long option_id,Long menu_item_id,String code,String name_zh,String name_en,
        String option_type,String option_group,BigDecimal price_delta,Boolean active,Integer sort_order,
        Long parent_option_id,Long store_remove_id,Long store_remove_store_id,boolean effective_active) {
        OptionSnapshot invariant() {
            return new OptionSnapshot(option_id,menu_item_id,code,name_zh,name_en,option_type,option_group,price_delta,
                active,sort_order,parent_option_id,null,null,effective_active);
        }
    }
    public record Master(Long id,String code,String name_zh,String name_en,boolean active,int sort_order) {}
    public record Decision(String semantic_code,Long existing_master_id,String master_code,String default_name,
        boolean create_master,List<Long> safe_option_ids,String conflict) {}
    public record Link(Long option_id,Long menu_item_id,String semantic_code,Long before_master_id,Long after_master_id) {}
    public record Conflict(String semantic_code,List<Long> option_ids,String reason) {}
    public record Report(Long store_id,boolean dry_run,int existing_shared_count,int resulting_shared_count,
        int created_master_count,int linked_option_count,int menu_item_eligibility_changes,
        String before_fingerprint,String after_fingerprint,List<Decision> decisions,List<Link> links,
        List<Conflict> conflicts,List<OptionSnapshot> before,List<OptionSnapshot> after) {}
    private final JdbcTemplate jdbc;
    private final MenuRevisionService revisions;
    private final EntityManager entities;
    private final ObjectMapper json = new ObjectMapper();

    public StoreRemoveBaselineReconciler(JdbcTemplate jdbc,MenuRevisionService revisions,EntityManager entities) {
        this.jdbc=jdbc;this.revisions=revisions;this.entities=entities;
    }

    @Transactional
    public Report reconcile(Request request,AuthenticatedUser actor) {
        if(request==null || request.store_id==null || request.dry_run==null) throw failure("REMOVE_BASELINE_REQUEST_REQUIRED");
        Long store=request.store_id;
        if(!Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from stores where id=?)",Boolean.class,store))) throw failure("REMOVE_STORE_REQUIRED");
        if(!request.dry_run) { entities.flush();revisions.lockStoresInOrder(List.of(store)); }
        List<OptionSnapshot> before=snapshot(store);
        List<Master> masters=masters(store);
        String fingerprint=fingerprint(store,before,masters);
        if(!request.dry_run && !Objects.equals(fingerprint,request.expected_before_fingerprint)) throw failure("REMOVE_BASELINE_CHANGED: run dry-run again");
        List<Conflict> conflicts=new ArrayList<>();
        List<Decision> decisions=plan(store,before,masters,conflicts);
        List<Link> links=new ArrayList<>();
        int created=0;
        int linked=0;
        for(Decision decision:decisions) {
            if(decision.create_master()) created++;
            linked+=decision.safe_option_ids().size();
            if(request.dry_run) {
                for (Long optionId : decision.safe_option_ids()) {
                    OptionSnapshot option = before.stream().filter(o -> o.option_id().equals(optionId)).findFirst().orElseThrow();
                    links.add(new Link(optionId, option.menu_item_id(), decision.semantic_code(), option.store_remove_id(), decision.existing_master_id()));
                }
                continue;
            }
            Long master=decision.existing_master_id();
            if(decision.create_master()) master=insertMaster(store,decision.master_code(),decision.default_name(),masters.size()+created);
            if(master==null) continue;
            for(Long optionId:decision.safe_option_ids()) {
                OptionSnapshot option=before.stream().filter(o->o.option_id().equals(optionId)).findFirst().orElseThrow();
                String mode=Boolean.TRUE.equals(option.active())?"ALLOW":"DENY";
                Integer existing=jdbc.queryForObject("select count(*) from store_remove_item_rules where remove_id=? and menu_item_id=?",Integer.class,master,option.menu_item_id());
                if(existing!=null && existing==0) jdbc.update("insert into store_remove_item_rules(remove_id,store_id,menu_item_id,mode) values (?,?,?,?)",master,store,option.menu_item_id(),mode);
                if(jdbc.update("update menu_item_options set store_remove_id=?,store_remove_store_id=? where id=? and menu_item_id=? and store_remove_id is null",master,store,option.option_id(),option.menu_item_id())!=1) throw failure("REMOVE_BASELINE_LINK_CHANGED");
                links.add(new Link(option.option_id(),option.menu_item_id(),decision.semantic_code(),option.store_remove_id(),master));
            }
        }
        List<OptionSnapshot> after=request.dry_run?before:snapshot(store);
        if(!before.stream().map(OptionSnapshot::invariant).toList().equals(after.stream().map(OptionSnapshot::invariant).toList())) throw failure("REMOVE_BASELINE_INVARIANT_CHANGED");
        String afterFingerprint=request.dry_run?fingerprint:fingerprint(store,after,masters(store));
        Report report=new Report(store,request.dry_run,masters.size(),masters.size()+created,created,linked,0,fingerprint,afterFingerprint,
            decisions,links,conflicts,before,after);
        if(!request.dry_run && (created>0 || linked>0)) {
            revisions.incrementRevision(store);
            // AuditLogService intentionally suppresses failures; this migration requires an atomic durable audit.
            jdbc.update("""
                insert into audit_logs(store_id,actor_user_id,actor_name_snapshot,actor_role_snapshot,action,entity_type,entity_id,
                summary,metadata_json,created_at) values (?,?,?,?, 'MENU_REMOVE_BASELINE_RECONCILED','STORE',?, ?,?,current_timestamp)
                """,store,actor==null?null:actor.userId(),actor==null?"SYSTEM":actor.fullName(),actor==null?"SYSTEM":actor.roleCode(),store,
                "Completed bounded 18-semantic Remove baseline without changing item choices",serialize(report));
        }
        return report;
    }

    private List<Decision> plan(Long store,List<OptionSnapshot> options,List<Master> masters,List<Conflict> conflicts) {
        Map<String,List<OptionSnapshot>> bySemantic=new LinkedHashMap<>();
        for(OptionSnapshot option:options) {
            String semantic=RemoveSemantics.canonicalSemantic(option.code());
            if(option.parent_option_id()!=null || "COMBO_SIDE_REMOVE".equalsIgnoreCase(option.option_group())) {
                if(option.store_remove_id()==null) conflicts.add(new Conflict(semantic,List.of(option.option_id()),"REMOVE_PARENT_RETAINED"));
                continue;
            }
            if(!RemoveSemantics.baselineDefaults().containsKey(semantic)) {
                if(option.store_remove_id()==null) conflicts.add(new Conflict(semantic,List.of(option.option_id()),semantic==null?"REMOVE_CODE_MISSING":"REMOVE_OUTSIDE_BASELINE"));
                continue;
            }
            bySemantic.computeIfAbsent(semantic,ignored->new ArrayList<>()).add(option);
        }
        List<Decision> result=new ArrayList<>();
        RemoveSemantics.baselineDefaults().forEach((semantic,defaultName)-> {
            List<Master> matching=masters.stream().filter(m->Objects.equals(semantic,RemoveSemantics.canonicalSemantic(m.code()))).toList();
            List<OptionSnapshot> rows=bySemantic.getOrDefault(semantic,List.of());
            List<OptionSnapshot> unlinked=rows.stream().filter(o->o.store_remove_id()==null).toList();
            Master master=matching.size()==1?matching.get(0):null;
            String conflict=matching.size()>1?"REMOVE_SEMANTIC_MASTER_DUPLICATE":null;
            if(conflict==null && !unlinked.isEmpty()) {
                OptionSnapshot first=rows.get(0);
                if(rows.stream().anyMatch(o->o.name_zh()==null || o.name_zh().isBlank() || o.price_delta()==null
                    || o.price_delta().signum()!=0 || o.active()==null || !"remove".equals(o.option_type()) || !"REMOVE".equals(o.option_group()))) conflict="REMOVE_VALUES_INVALID";
                else if(rows.stream().anyMatch(o->!Objects.equals(first.name_zh(),o.name_zh()) || !Objects.equals(first.name_en(),o.name_en()))) conflict="REMOVE_BUSINESS_VALUES_CONFLICT";
                else if(rows.stream().map(OptionSnapshot::menu_item_id).distinct().count()!=rows.size()) conflict="REMOVE_RELATIONSHIP_CONFLICT";
                else if(master!=null && !master.active() && unlinked.stream().anyMatch(o->Boolean.TRUE.equals(o.active()))) conflict="REMOVE_MASTER_INACTIVE_CONFLICT";
                else if(master!=null && unlinked.stream().anyMatch(o->!canRetainEligibility(store,master.id(),o))) conflict="REMOVE_APPLICABILITY_CONFLICT";
            }
            if(conflict!=null) conflicts.add(new Conflict(semantic,unlinked.stream().map(OptionSnapshot::option_id).toList(),conflict));
            result.add(new Decision(semantic,master==null?null:master.id(),master==null?semantic:master.code(),
                master==null?defaultName:master.name_zh(),matching.isEmpty(),conflict==null?unlinked.stream().map(OptionSnapshot::option_id).toList():List.of(),conflict));
        });
        return result;
    }

    private boolean canRetainEligibility(Long store,Long master,OptionSnapshot row) {
        List<String> rules=jdbc.query("select mode from store_remove_item_rules where remove_id=? and store_id=? and menu_item_id=?",(rs,n)->rs.getString(1),master,store,row.menu_item_id());
        return rules.isEmpty() || Objects.equals("ALLOW".equals(rules.get(0)),Boolean.TRUE.equals(row.active()));
    }

    private List<OptionSnapshot> snapshot(Long store) {
        return jdbc.query("""
            select o.*, case when o.store_remove_id is null then coalesce(o.is_active,false)
             else coalesce(o.is_active,false) and r.active and
              (coalesce(rule.mode='ALLOW',false) or (rule.mode is null and exists (
                select 1 from store_remove_categories c where c.remove_id=r.id and c.store_id=i.store_id and c.category_id=i.category_id))) end effective_active
            from menu_item_options o join menu_items i on i.id=o.menu_item_id
            left join store_removes r on r.id=o.store_remove_id and r.store_id=i.store_id
            left join store_remove_item_rules rule on rule.remove_id=r.id and rule.menu_item_id=i.id
            where i.store_id=? and (lower(o.option_type)='remove' or upper(o.option_group) in ('REMOVE','COMBO_SIDE_REMOVE'))
            order by o.id
            """,(rs,n)->new OptionSnapshot(rs.getLong("id"),rs.getLong("menu_item_id"),rs.getString("option_code"),rs.getString("name_zh"),rs.getString("name_en"),
                rs.getString("option_type"),rs.getString("option_group"),rs.getBigDecimal("price_delta"),rs.getObject("is_active",Boolean.class),rs.getObject("sort_order",Integer.class),
                rs.getObject("parent_option_id",Long.class),rs.getObject("store_remove_id",Long.class),rs.getObject("store_remove_store_id",Long.class),rs.getBoolean("effective_active")),store);
    }

    private List<Master> masters(Long store) {
        return jdbc.query("select id,code,name_zh,name_en,active,sort_order from store_removes where store_id=? order by id",
            (rs,n)->new Master(rs.getLong("id"),rs.getString("code"),rs.getString("name_zh"),rs.getString("name_en"),rs.getBoolean("active"),rs.getInt("sort_order")),store);
    }
    private String fingerprint(Long store,List<OptionSnapshot> options,List<Master> masters) {
        Map<String,Object> data=new LinkedHashMap<>();data.put("store",store);data.put("options",options);data.put("masters",masters);
        data.put("item_rules",jdbc.queryForList("select remove_id,menu_item_id,mode from store_remove_item_rules where store_id=? order by remove_id,menu_item_id",store));
        data.put("categories",jdbc.queryForList("select remove_id,category_id from store_remove_categories where store_id=? order by remove_id,category_id",store));
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(serialize(data).getBytes(StandardCharsets.UTF_8))); }
        catch(NoSuchAlgorithmException exception) { throw new IllegalStateException(exception); }
    }
    private Long insertMaster(Long store,String code,String name,int order) {
        GeneratedKeyHolder key=new GeneratedKeyHolder();
        jdbc.update(connection->{var statement=connection.prepareStatement("insert into store_removes(store_id,code,name_zh,name_en,active,sort_order,created_at,updated_at) values (?,?,?,null,true,?,current_timestamp,current_timestamp)",new String[]{"id"});
            statement.setLong(1,store);statement.setString(2,code);statement.setString(3,name);statement.setInt(4,order*10);return statement;},key);
        return Objects.requireNonNull(key.getKey()).longValue();
    }
    private String serialize(Object value) {
        try { return json.writeValueAsString(value); }
        catch(JsonProcessingException exception) { throw failure("REMOVE_BASELINE_REPORT_INVALID"); }
    }
    private static BusinessException failure(String message) { return new BusinessException(message); }
}
