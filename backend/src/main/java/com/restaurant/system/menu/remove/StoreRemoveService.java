package com.restaurant.system.menu.remove;

import com.restaurant.system.common.exception.BusinessException;
import com.restaurant.system.menu.service.MenuRevisionService;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Current Store master data and compatibility assignments only; never changes frozen orders. */
@Service
public class StoreRemoveService {
    public record Remove(Long id, Long store_id, String code, String name_zh, String name_en,
                         boolean active, int sort_order, List<Long> item_ids) {}
    public record Conflict(String code, List<Long> option_ids, List<String> names_zh,
                           List<String> names_en, String reason) {}
    public record LegacyOption(Long option_id, Long item_id, String code, String name_zh, String name_en, Boolean active, String option_group, Long parent_option_id) {}
    public record Catalog(List<Remove> removes, List<Conflict> conflicts, List<LegacyOption> legacy_options) {}
    public record ItemRule(Long item_id, String name_zh, String name_en, Long category_id,
                           String mode, boolean effective) {}
    public record Applicability(Long remove_id, Long store_id, List<Long> category_ids, List<ItemRule> items) {}
    public record Reconciliation(Long store_id, boolean dry_run, int linked_groups,
                                 int linked_options, List<Conflict> conflicts) {}
    public static class WriteRequest {
        public Long store_id;
        public String code;
        public String name_zh;
        public String name_en;
        public Boolean active;
        public Integer sort_order;
    }
    public static class ReorderRequest { public Long store_id; public List<Long> ids; }
    public static class RuleRequest { public Long item_id; public String mode; }
    public static class ApplicabilityRequest {
        public Long store_id;
        public List<Long> category_ids;
        public List<RuleRequest> items;
    }
    public static class ReconcileRequest { public Long store_id; public Boolean dry_run; }
    private record Legacy(Long id, Long item, String code, String zh, String en, BigDecimal price,
                          Boolean active, Integer sort, Long parent, Long master) {}
    private record Plan(String code, List<Legacy> rows, Remove master, String conflict) {}
    private final JdbcTemplate jdbc;
    private final MenuRevisionService revisions;
    private final EntityManager entityManager;

    public StoreRemoveService(JdbcTemplate jdbc, MenuRevisionService revisions, EntityManager entityManager) {
        this.jdbc = jdbc;
        this.revisions = revisions;
        this.entityManager = entityManager;
    }

    @Transactional(readOnly = true)
    public Catalog getRemoves(Long storeId) {
        requireStore(storeId);
        List<Remove> entries = masters(storeId);
        return new Catalog(entries, conflicts(plan(storeId, entries)), legacyOptions(storeId));
    }

    @Transactional(readOnly = true)
    public Long storeIdForRemove(Long id) { return requireRemove(id).store_id(); }

    @Transactional
    public Remove create(WriteRequest request) {
        if (request == null) throw failure("REMOVE_PAYLOAD_REQUIRED");
        String code = code(request.code);
        validateValues(request);
        lock(request.store_id);
        if (masters(request.store_id).stream().anyMatch(r -> r.code().equals(code))) throw failure("REMOVE_CODE_EXISTS");
        if (legacy(request.store_id).stream().anyMatch(r -> code.equals(r.code()))) throw failure("REMOVE_LEGACY_CONFLICT");
        long id = insert("""
            insert into store_removes(store_id,code,name_zh,name_en,active,sort_order,created_at,updated_at)
            values (?,?,?,?,?,?,current_timestamp,current_timestamp)
            """, request.store_id, code, request.name_zh.trim(), text(request.name_en), request.active,
            request.sort_order == null ? 0 : request.sort_order);
        revisions.incrementRevision(request.store_id);
        return requireRemove(id);
    }

    @Transactional
    public Remove update(Long id, WriteRequest request) {
        Remove before = requireRemove(id);
        lock(before.store_id());
        before = requireRemove(id);
        validateValues(request);
        checkScope(before.store_id(), request.store_id);
        if (request.code != null && !before.code().equals(code(request.code))) throw failure("REMOVE_CODE_IMMUTABLE");
        jdbc.update("""
            update store_removes set name_zh=?,name_en=?,active=?,sort_order=?,updated_at=current_timestamp
            where id=? and store_id=?
            """, request.name_zh.trim(), text(request.name_en), request.active,
            request.sort_order == null ? before.sort_order() : request.sort_order, id, before.store_id());
        refreshStoreAssignments(before.store_id());
        revisions.incrementRevision(before.store_id());
        return requireRemove(id);
    }

    @Transactional
    public List<Remove> reorder(ReorderRequest request) {
        if (request == null || request.ids == null) throw failure("REMOVE_REORDER_REQUIRED");
        lock(request.store_id);
        List<Long> current = masters(request.store_id).stream().map(Remove::id).toList();
        if (request.ids.size() != current.size() || new HashSet<>(request.ids).size() != current.size()
            || !new HashSet<>(current).equals(new HashSet<>(request.ids))) throw failure("REMOVE_REORDER_SCOPE_INVALID");
        for (int i = 0; i < request.ids.size(); i++) {
            jdbc.update("update store_removes set sort_order=?,updated_at=current_timestamp where id=? and store_id=?",
                i * 10, request.ids.get(i), request.store_id);
        }
        refreshStoreAssignments(request.store_id);
        revisions.incrementRevision(request.store_id);
        return masters(request.store_id);
    }

    @Transactional(readOnly = true)
    public Applicability getApplicability(Long id) {
        Remove remove = requireRemove(id);
        List<Long> categories = categoryIds(id);
        Map<Long,String> overrides = overrides(id);
        List<ItemRule> items = jdbc.query("select id,name_zh,name_en,category_id from menu_items where store_id=? order by id",
            (rs,n) -> {
                Long item = rs.getLong("id");
                Long category = rs.getObject("category_id",Long.class);
                String mode = overrides.getOrDefault(item,"INHERIT");
                return new ItemRule(item,rs.getString("name_zh"),rs.getString("name_en"),category,
                    mode,effective(mode,categories.contains(category)));
            },remove.store_id());
        return new Applicability(id,remove.store_id(),categories,items);
    }

    @Transactional
    public Applicability setApplicability(Long id, ApplicabilityRequest request) {
        Remove remove = requireRemove(id);
        lock(remove.store_id());
        checkScope(remove.store_id(),request == null ? null : request.store_id);
        if (request == null || request.category_ids == null || request.items == null) throw failure("REMOVE_APPLICABILITY_REQUIRED");
        if (new HashSet<>(request.category_ids).size() != request.category_ids.size()) throw failure("REMOVE_CATEGORY_DUPLICATE");
        for (Long category : request.category_ids) {
            requireMembership("menu_categories",category,remove.store_id());
        }
        HashSet<Long> seen = new HashSet<>();
        for (RuleRequest rule : request.items) {
            if (rule == null || rule.item_id == null || !seen.add(rule.item_id) || rule.mode == null
                || !List.of("ALLOW","DENY","INHERIT").contains(rule.mode)) throw failure("REMOVE_ITEM_RULE_INVALID");
            requireMembership("menu_items",rule.item_id,remove.store_id());
        }
        jdbc.update("delete from store_remove_categories where remove_id=? and store_id=?",id,remove.store_id());
        jdbc.update("delete from store_remove_item_rules where remove_id=? and store_id=?",id,remove.store_id());
        for (Long category : request.category_ids) jdbc.update("insert into store_remove_categories(remove_id,store_id,category_id) values (?,?,?)",id,remove.store_id(),category);
        for (RuleRequest rule : request.items) if (!"INHERIT".equals(rule.mode)) {
            jdbc.update("insert into store_remove_item_rules(remove_id,store_id,menu_item_id,mode) values (?,?,?,?)",id,remove.store_id(),rule.item_id,rule.mode);
        }
        refreshStoreAssignments(remove.store_id());
        revisions.incrementRevision(remove.store_id());
        return getApplicability(id);
    }

    /** Called inside menu Item/category mutations too. No revision increment: caller owns it. */
    @Transactional
    public void refreshStoreAssignments(Long storeId) {
        entityManager.flush();
        revisions.lockStoresInOrder(List.of(storeId));
        for (Remove remove : masters(storeId)) {
            Applicability rules = getApplicability(remove.id());
            for (ItemRule item : rules.items()) {
                List<Long> linked = jdbc.query("select id from menu_item_options where menu_item_id=? and store_remove_id=?",
                    (rs,n)->rs.getLong(1),item.item_id(),remove.id());
                if (linked.isEmpty() && item.effective()) {
                    // An unresolved old identity must never be silently replaced by a second row.
                    Integer existing = jdbc.queryForObject("select count(*) from menu_item_options where menu_item_id=? and option_code=? and store_remove_id is null",Integer.class,item.item_id(),remove.code());
                    if (existing != null && existing > 0) throw failure("REMOVE_LEGACY_CONFLICT");
                    jdbc.update("""
                        insert into menu_item_options(menu_item_id,option_type,option_code,option_group,name_zh,name_en,
                        price_delta,is_active,sort_order,store_remove_id,store_remove_store_id,created_at,updated_at)
                        values (?,'remove',?,'REMOVE',?,?,0,?,?,?,?,current_timestamp,current_timestamp)
                        """,item.item_id(),remove.code(),remove.name_zh(),remove.name_en(),
                        remove.active() && item.effective(),remove.sort_order(),remove.id(),storeId);
                } else {
                    jdbc.update("""
                        update menu_item_options set option_type='remove',option_group='REMOVE',option_code=?,name_zh=?,name_en=?,
                        price_delta=0,is_active=?,sort_order=?,updated_at=current_timestamp
                        where menu_item_id=? and store_remove_id=? and store_remove_store_id=?
                        """,remove.code(),remove.name_zh(),remove.name_en(),remove.active() && item.effective(),
                        remove.sort_order(),item.item_id(),remove.id(),storeId);
                }
            }
        }
    }

    @Transactional
    public Reconciliation reconcile(Long storeId, boolean dryRun) {
        return reconcile(storeId, dryRun, true);
    }

    private Reconciliation reconcile(Long storeId, boolean dryRun, boolean bumpRevision) {
        if (dryRun) requireStore(storeId); else lock(storeId);
        List<Plan> plans = plan(storeId,masters(storeId));
        int groups = 0;
        int options = 0;
        for (Plan plan : plans) {
            if (plan.conflict() != null) continue;
            List<Legacy> unlinked = plan.rows().stream().filter(r->r.master()==null).toList();
            if (unlinked.isEmpty()) continue;
            groups++;
            options += unlinked.size();
            if (dryRun) continue;
            Legacy first = plan.rows().get(0);
            long id = plan.master()==null ? insert("""
                insert into store_removes(store_id,code,name_zh,name_en,active,sort_order,created_at,updated_at)
                values (?,?,?,?,?,?,current_timestamp,current_timestamp)
                """,storeId,plan.code(),first.zh(),first.en(),plan.rows().stream().anyMatch(r->Boolean.TRUE.equals(r.active())),
                plan.rows().stream().map(Legacy::sort).filter(Objects::nonNull).min(Integer::compareTo).orElse(0)) : plan.master().id();
            HashSet<Long> configured = new HashSet<>(overrides(id).keySet());
            for (Legacy row : unlinked) {
                if (configured.add(row.item())) jdbc.update("insert into store_remove_item_rules(remove_id,store_id,menu_item_id,mode) values (?,?,?,?)",
                    id,storeId,row.item(),Boolean.TRUE.equals(row.active())?"ALLOW":"DENY");
                jdbc.update("update menu_item_options set store_remove_id=?,store_remove_store_id=?,option_type='remove',option_group='REMOVE' where id=? and store_remove_id is null",id,storeId,row.id());
            }
        }
        if (!dryRun && options > 0) {
            refreshStoreAssignments(storeId);
            if (bumpRevision) revisions.incrementRevision(storeId);
        }
        return new Reconciliation(storeId,dryRun,groups,options,conflicts(plans));
    }

    /** Provisioning/clone already own their single revision increment. */
    @Transactional
    public Reconciliation reconcileForMaterialization(Long storeId) {
        return reconcile(storeId,false,false);
    }

    private List<Plan> plan(Long storeId,List<Remove> masters) {
        Map<String,List<Legacy>> groups = new LinkedHashMap<>();
        legacy(storeId).forEach(row->groups.computeIfAbsent(row.code(),ignored->new ArrayList<>()).add(row));
        List<Plan> plans = new ArrayList<>();
        groups.forEach((code,rows)-> {
            if (rows.stream().noneMatch(r->r.master()==null) && rows.stream().map(Legacy::item).distinct().count()==rows.size()) return;
            Remove master = masters.stream().filter(m->Objects.equals(m.code(),code)).findFirst().orElse(null);
            Legacy first = rows.get(0);
            String conflict = null;
            if (!validCode(code)) conflict="REMOVE_CODE_INVALID";
            else if (rows.stream().anyMatch(r->text(r.zh())==null || r.price()==null || r.price().signum()!=0 || r.parent()!=null || r.active()==null)) conflict="REMOVE_VALUES_INVALID";
            else if (rows.stream().anyMatch(r->!Objects.equals(r.zh(),first.zh()) || !Objects.equals(r.en(),first.en()))) conflict="REMOVE_BUSINESS_VALUES_CONFLICT";
            else if (master!=null && (!Objects.equals(master.name_zh(),first.zh()) || !Objects.equals(master.name_en(),first.en()))) conflict="REMOVE_CATALOG_VALUES_CONFLICT";
            Map<Long,Boolean> active = new LinkedHashMap<>();
            for (Legacy row:rows) {
                if (active.containsKey(row.item())) conflict="REMOVE_RELATIONSHIP_CONFLICT";
                active.put(row.item(),row.active());
            }
            plans.add(new Plan(code,rows,master,conflict));
        });
        return plans;
    }

    private List<LegacyOption> legacyOptions(Long storeId) {
        return jdbc.query("""
            select o.* from menu_item_options o join menu_items i on i.id=o.menu_item_id
            where i.store_id=? and o.store_remove_id is null
            and (upper(o.option_group) in ('REMOVE','COMBO_SIDE_REMOVE')
            or ((o.option_group is null or trim(o.option_group)='') and lower(o.option_type)='remove'))
            order by o.menu_item_id,o.sort_order asc nulls last,o.id
            """,(rs,n)->new LegacyOption(rs.getLong("id"),rs.getLong("menu_item_id"),rs.getString("option_code"),
            rs.getString("name_zh"),rs.getString("name_en"),rs.getObject("is_active",Boolean.class),
            rs.getString("option_group"),rs.getObject("parent_option_id",Long.class)),storeId);
    }

    private List<Legacy> legacy(Long storeId) {
        return jdbc.query("""
            select o.* from menu_item_options o join menu_items i on i.id=o.menu_item_id where i.store_id=?
            and (upper(o.option_group)='REMOVE' or ((o.option_group is null or trim(o.option_group)='') and lower(o.option_type)='remove'))
            order by o.option_code asc nulls first,o.id
            """,(rs,n)->new Legacy(rs.getLong("id"),rs.getLong("menu_item_id"),rs.getString("option_code"),rs.getString("name_zh"),rs.getString("name_en"),
            rs.getBigDecimal("price_delta"),rs.getObject("is_active",Boolean.class),rs.getObject("sort_order",Integer.class),rs.getObject("parent_option_id",Long.class),rs.getObject("store_remove_id",Long.class)),storeId);
    }

    private List<Conflict> conflicts(List<Plan> plans) {
        return plans.stream().filter(p->p.conflict()!=null).map(p->new Conflict(p.code(),p.rows().stream().map(Legacy::id).toList(),
            p.rows().stream().map(Legacy::zh).filter(Objects::nonNull).distinct().toList(),p.rows().stream().map(Legacy::en).filter(Objects::nonNull).distinct().toList(),p.conflict())).toList();
    }

    private List<Remove> masters(Long storeId) {
        return jdbc.query("select * from store_removes where store_id=? order by sort_order,id",(rs,n)-> {
            Long id=rs.getLong("id");
            return new Remove(id,rs.getLong("store_id"),rs.getString("code"),rs.getString("name_zh"),rs.getString("name_en"),rs.getBoolean("active"),rs.getInt("sort_order"),effectiveItemIds(id,storeId));
        },storeId);
    }

    private Remove requireRemove(Long id) {
        List<Long> stores=jdbc.query("select store_id from store_removes where id=?",(rs,n)->rs.getLong(1),id);
        if (stores.isEmpty()) throw failure("REMOVE_NOT_FOUND");
        return masters(stores.get(0)).stream().filter(r->r.id().equals(id)).findFirst().orElseThrow(()->failure("REMOVE_NOT_FOUND"));
    }

    private List<Long> effectiveItemIds(Long id,Long storeId) {
        return jdbc.query("""
            select i.id from menu_items i left join store_remove_item_rules r on r.menu_item_id=i.id and r.remove_id=?
            where i.store_id=? and (r.mode='ALLOW' or (r.mode is null and exists (
            select 1 from store_remove_categories c where c.remove_id=? and c.store_id=i.store_id and c.category_id=i.category_id))) order by i.id
            """,(rs,n)->rs.getLong(1),id,storeId,id);
    }

    private List<Long> categoryIds(Long id) { return jdbc.query("select category_id from store_remove_categories where remove_id=? order by category_id",(rs,n)->rs.getLong(1),id); }
    private Map<Long,String> overrides(Long id) {
        Map<Long,String> result=new LinkedHashMap<>();
        jdbc.query("select menu_item_id,mode from store_remove_item_rules where remove_id=?",rs->{result.put(rs.getLong(1),rs.getString(2));},id);
        return result;
    }
    private void requireMembership(String table,Long id,Long storeId) {
        // Table is an internal constant, never input.
        if (id==null || !Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from "+table+" where id=? and store_id=?)",Boolean.class,id,storeId))) throw failure("REMOVE_STORE_MISMATCH");
    }
    private void lock(Long storeId) { requireStore(storeId); entityManager.flush(); revisions.lockStoresInOrder(List.of(storeId)); }
    private void requireStore(Long storeId) {
        if (storeId==null || !Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from stores where id=?)",Boolean.class,storeId))) throw failure("REMOVE_STORE_REQUIRED");
    }
    public static void checkScope(Long actual,Long supplied) { if (supplied!=null && !Objects.equals(actual,supplied)) throw failure("REMOVE_STORE_MISMATCH"); }
    private static boolean effective(String mode,boolean category) { return "ALLOW".equals(mode) || ("INHERIT".equals(mode) && category); }
    private void validateValues(WriteRequest request) {
        if(request==null || text(request.name_zh)==null || request.name_zh.trim().length()>255 || (request.name_en!=null && request.name_en.trim().length()>255) || request.active==null) throw failure("REMOVE_VALUES_INVALID");
    }
    private static String code(String input) {
        String value=input==null?null:input.trim().toLowerCase(Locale.ROOT);
        if(!validCode(value)) throw failure("REMOVE_CODE_INVALID");
        return value;
    }
    private static boolean validCode(String value) { return value!=null && value.length()<=255 && value.matches("[a-z][a-z0-9]*(?:_[a-z0-9]+)*"); }
    private static String text(String value) { return value==null || value.isBlank()?null:value.trim(); }
    private long insert(String sql,Object...args) {
        GeneratedKeyHolder key=new GeneratedKeyHolder();
        jdbc.update(connection->{var statement=connection.prepareStatement(sql,new String[]{"id"});for(int i=0;i<args.length;i++) statement.setObject(i+1,args[i]);return statement;},key);
        return Objects.requireNonNull(key.getKey()).longValue();
    }
    private static BusinessException failure(String message) { return new BusinessException(message); }
}
