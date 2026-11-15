package com.restaurant.system.allo;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurant.system.common.exception.BusinessException;
import com.restaurant.system.order.entity.*;
import com.restaurant.system.order.repository.*;
import com.restaurant.system.printing.AlloProductionPrintingPolicy;
import com.restaurant.system.user.repository.StoreRepository;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@Service
public class AlloSalesService {
    public record VoidLine(Long item_id, Integer quantity) {}
    public record Adjustment(String request_key, Integer expected_revision, String action,
                             List<VoidLine> items, String discount_type, BigDecimal discount_value,
                             String discount_name, String reason) {}
    public record Preset(Long id, String name, String discount_type, BigDecimal value, Boolean enabled, Integer revision) {}
    @org.springframework.beans.factory.annotation.Autowired
    private com.restaurant.system.analytics.service.AnalyticsAggregationService analytics;
    @jakarta.persistence.PersistenceContext
    private jakarta.persistence.EntityManager entityManager;
    private final OrderRepository orders;
    private final OrderItemRepository items;
    private final StoreRepository stores;
    private final AlloProductionPrintingPolicy policy;
    private final JdbcTemplate db;
    private final ObjectMapper json;
    public AlloSalesService(OrderRepository orders, OrderItemRepository items, StoreRepository stores,
                           AlloProductionPrintingPolicy policy, JdbcTemplate db, ObjectMapper json) {
        this.orders=orders;this.items=items;this.stores=stores;this.policy=policy;this.db=db;this.json=json;
    }
    public boolean enabled(Long storeId) { return policy.allowsPadDirect(storeId); }
    public void requireEnabled(Long storeId) {
        if (!enabled(storeId)) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"ALLO_SALES_NOT_ENABLED");
    }
    public LocalDate today(Long storeId) {
        return LocalDate.now(ZoneId.of(stores.findById(storeId).orElseThrow().timezone == null
            ? "America/Toronto" : stores.findById(storeId).orElseThrow().timezone));
    }
    public static LocalDate businessDate(Order o) { return o.submitted_at == null ? null : o.submitted_at.toLocalDate(); }
    public void recalculate(Order o, List<OrderItem> lines) {
        BigDecimal gross=lines.stream().map(AlloMoney::effectiveLine).reduce(BigDecimal.ZERO,BigDecimal::add);
        var m=AlloMoney.calculate(gross,o.discount_type,o.discount_value,"completed".equals(o.status));
        o.subtotal_amount=m.gross();o.discount_amount=m.discount();o.tax_amount=m.tax();o.total_amount=m.total();
    }
    @Transactional(readOnly=true)
    public Map<String,Object> detail(Long id) {
        Order o=orders.findById(id).orElseThrow(); requireEnabled(o.store_id);
        Map<String,Object> result=new LinkedHashMap<>();result.put("order",o);result.put("amounts",AlloMoney.determined(o));
        result.put("items",items.findAllByOrderId(id).stream().map(i->{var v=new LinkedHashMap<String,Object>();v.put("id",i.id);v.put("quantity",i.quantity);v.put("voided_quantity",i.voided_quantity);v.put("combo_role",i.combo_role);v.put("status",i.status);v.put("item_name_snapshot_zh",i.item_name_snapshot_zh);v.put("item_name_snapshot_en",i.item_name_snapshot_en);v.put("line_amount",i.line_amount);return v;}).toList());result.put("business_date",businessDate(o));
        result.put("editable",today(o.store_id).equals(businessDate(o)) && !Set.of("draft","cancelled").contains(o.status));
        result.put("adjustments",db.queryForList("select a.*,o.order_no,o.order_type,o.table_no,o.pickup_no from allo_order_adjustments a join orders o on o.id=a.order_id where a.order_id=? order by a.id",id));
        result.put("printing_notice","已排队或执行的小票保留原内容；本次不会重打，也不发送取消票。");
        return result;
    }
    @Transactional
    public Map<String,Object> adjust(Long id, Adjustment r, Long actorId, String actorName) {
        return adjustPlan(id,r,actorId,actorName,false);
    }
    @Transactional(readOnly=true)
    public Map<String,Object> preview(Long id,Adjustment r) {return adjustPlan(id,r,0L,"preview",true);}
    private Map<String,Object> adjustPlan(Long id,Adjustment r,Long actorId,String actorName,boolean preview) {
        if (r == null || r.request_key()==null || !r.request_key().matches("[A-Za-z0-9_-]{16,100}")
            || r.expected_revision()==null || r.reason()!=null && r.reason().length()>500)
            throw new BusinessException("ADJUSTMENT_REQUEST_INVALID");
        Order o=preview ? orders.findById(id).orElse(null) : orders.findByIdForUpdate(id); if(o==null)throw new BusinessException("ORDER_NOT_FOUND");requireEnabled(o.store_id);
        if(!preview)entityManager.refresh(o,jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        if(preview) {var copy=new Order();com.restaurant.system.allo.AlloCopies.copy(o,copy);o=copy;}
        String hash=hash(write(r));
        var replay=db.queryForList("select request_hash from allo_order_adjustments where order_id=? and request_key=?",id,r.request_key());
        if(!replay.isEmpty()) {
            if(!hash.equals(replay.get(0).get("request_hash").toString().trim()))throw conflict("IDEMPOTENCY_CONFLICT");
            return detail(id);
        }
        if (!today(o.store_id).equals(businessDate(o)) || o.kitchenMirror() || o.external_source!=null
            || Set.of("draft","cancelled").contains(o.status)) throw conflict("CURRENT_BUSINESS_DAY_ORDER_REQUIRED");
        if(!Objects.equals(o.current_revision == null ? 0 : o.current_revision,r.expected_revision()))throw conflict("ORDER_REVISION_STALE");
        var before=AlloMoney.determined(o); var beforeSnapshot=financialSnapshot(o);
        List<OrderItem> lines=items.findAllByOrderId(id);
        if(preview)lines=lines.stream().map(i->{var c=new OrderItem();com.restaurant.system.allo.AlloCopies.copy(i,c);return c;}).toList();
        List<Map<String,Object>> changes=new ArrayList<>();
        if ("VOID_ORDER".equals(r.action()) || "VOID_ITEMS".equals(r.action())) {
            Map<Long,Integer> selected=new LinkedHashMap<>();
            if("VOID_ORDER".equals(r.action())) lines.stream().filter(i->AlloMoney.effectiveQuantity(i)>0 && !Set.of("combo_side","combo_egg").contains(i.combo_role==null?"":i.combo_role))
                .forEach(i->selected.put(i.id,AlloMoney.effectiveQuantity(i)));
            else {
                if(r.items()==null || r.items().isEmpty() || r.items().size()>300)throw new BusinessException("VOID_ITEMS_REQUIRED");
                for(var v:r.items()) if(v.item_id()==null || v.quantity()==null || v.quantity()<1 || selected.put(v.item_id(),v.quantity())!=null)
                    throw new BusinessException("VOID_QUANTITY_INVALID");
            }
            for(var e:selected.entrySet()) {
                var i=lines.stream().filter(l->l.id.equals(e.getKey())).findFirst().orElseThrow(()->new BusinessException("VOID_ITEM_SCOPE_INVALID"));
                if(e.getValue()>AlloMoney.effectiveQuantity(i))throw conflict("VOID_QUANTITY_INVALID");
                // A combo is one semantic unit; retain its child ownership and void proportional children.
                if("combo_side".equals(i.combo_role) || "combo_egg".equals(i.combo_role))throw new BusinessException("VOID_COMBO_MAIN_REQUIRED");
                voidLine(i,e.getValue(),changes);
                if(i.combo_group_no!=null && "main".equals(i.combo_role)) {
                    for(var child:lines) if(!child.id.equals(i.id) && Objects.equals(child.combo_group_no,i.combo_group_no)) {
                        if(!"VOID_ORDER".equals(r.action()) && child.quantity % i.quantity != 0)throw new BusinessException("VOID_COMBO_RATIO_INVALID");
                        int n="VOID_ORDER".equals(r.action())?AlloMoney.effectiveQuantity(child):e.getValue()*(child.quantity/i.quantity);
                        if(n>AlloMoney.effectiveQuantity(child))throw conflict("VOID_COMBO_QUANTITY_INVALID");
                        voidLine(child,n,changes);
                    }
                }
            }
            if(lines.stream().noneMatch(i->AlloMoney.effectiveQuantity(i)>0))o.status="cancelled";
        } else if ("DISCOUNT".equals(r.action())) {
            AlloMoney.validateDiscount(r.discount_type(),r.discount_value());
            if(r.discount_name()==null||r.discount_name().isBlank()||r.discount_name().length()>120)throw new BusinessException("DISCOUNT_NAME_INVALID");
            o.discount_type=r.discount_type();o.discount_value=r.discount_value();o.discount_name=r.discount_name();
        } else if ("REMOVE_DISCOUNT".equals(r.action())) { o.discount_type=null;o.discount_value=null;o.discount_name=null; }
        else throw new BusinessException("ADJUSTMENT_ACTION_INVALID");
        recalculate(o,lines);
        o.current_revision=(o.current_revision==null?0:o.current_revision)+1;o.updated_at=LocalDateTime.now();
        var after=AlloMoney.determined(o);
        if(preview)return Map.of("before",before,"after",after,"items",changes);
        items.saveAll(lines);orders.saveAndFlush(o);
        // Mandatory transaction ledger: an insert failure rolls back all item and money writes.
        db.update("""
            insert into allo_order_adjustments(store_id,order_id,business_date,request_key,request_hash,actor_id,actor_name,
            action,reason,before_snapshot,after_snapshot,item_changes,collected_delta,created_at)
            values (?,?,?,?,?,?,?,?,?,?,?,?,?,?)
            """,o.store_id,id,businessDate(o),r.request_key(),hash,actorId,actorName,r.action(),r.reason(),write(beforeSnapshot),write(financialSnapshot(o)),
            write(changes),after.collected().subtract(before.collected()),o.updated_at);
        analytics.rebuildForDate(businessDate(o),o.store_id);
        // Queue policy: preserve already-frozen physical intent and lease states. Never reset or reprint.
        // Original operational tasks remain the original production instruction; no cancellation notice or stock replay.
        return detail(id);
    }
    private Map<String,Object> financialSnapshot(Order o) {
        var m=AlloMoney.determined(o);var snapshot=new LinkedHashMap<String,Object>();
        snapshot.put("gross",m.gross());snapshot.put("discount",m.discount());snapshot.put("net",m.net());snapshot.put("tax",m.tax());snapshot.put("total",m.total());snapshot.put("collected",m.collected());
        var rule=new LinkedHashMap<String,Object>();rule.put("type",o.discount_type);rule.put("value",o.discount_value);rule.put("name",o.discount_name);snapshot.put("discount_rule",rule);
        snapshot.put("print_policy","PRESERVE_EXISTING_INTENT_AND_LEASE_NO_CANCEL_TICKET_NO_AUTOMATIC_REPRINT");
        snapshot.put("print_jobs",db.queryForList("select id,status,module_code,receipt_type from print_jobs where store_id=? and order_id=? order by id",o.store_id,o.id));
        snapshot.put("dispatch_events",db.queryForList("select id,status,module_code from order_dispatch_outbox where store_id=? and order_id=? order by id",o.store_id,o.id));
        return snapshot;
    }
    private void voidLine(OrderItem i,int n,List<Map<String,Object>> changes) {
        if(n==0)return;
        i.voided_quantity=(i.voided_quantity==null?0:i.voided_quantity)+n;
        changes.add(Map.of("item_id",i.id,"name",i.item_name_snapshot_zh==null?String.valueOf(i.menu_item_id):i.item_name_snapshot_zh,"quantity",n));

    }
    @Transactional(readOnly=true)
    public List<Map<String,Object>> presets(Long store) { requireEnabled(store);return db.queryForList("select * from allo_discount_presets where store_id=? order by id",store); }
    @Transactional
    public List<Map<String,Object>> savePreset(Long store,Preset p,Long actor) {
        requireEnabled(store);AlloMoney.validateDiscount(p.discount_type(),p.value());
        if(p.name()==null||p.name().isBlank()||p.name().length()>120)throw new BusinessException("DISCOUNT_NAME_INVALID");
        if(p.id()==null)db.update("insert into allo_discount_presets(store_id,name,discount_type,value,enabled,created_by,created_at,updated_at) values(?,?,?,?,?,?,?,?)",
            store,p.name(),p.discount_type(),p.value(),p.enabled()==null||p.enabled(),actor,LocalDateTime.now(),LocalDateTime.now());
        else if(db.update("update allo_discount_presets set name=?,discount_type=?,value=?,enabled=?,revision=revision+1,updated_at=? where id=? and store_id=? and revision=?",
            p.name(),p.discount_type(),p.value(),!Boolean.FALSE.equals(p.enabled()),LocalDateTime.now(),p.id(),store,p.revision())!=1)throw conflict("PRESET_REVISION_STALE");
        return presets(store);
    }
    public String write(Object o) { try{return json.writeValueAsString(o);}catch(Exception e){throw new BusinessException("FINANCIAL_SNAPSHOT_INVALID");} }
    private String hash(String s) { try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);} }
    private static ResponseStatusException conflict(String s){return new ResponseStatusException(HttpStatus.CONFLICT,s);}
}
