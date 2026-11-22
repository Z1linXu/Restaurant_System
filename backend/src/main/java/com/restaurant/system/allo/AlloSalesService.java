package com.restaurant.system.allo;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.annotation.JsonInclude;
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
    public record VoidAddon(Long option_id, Integer quantity) {}
    public record CouponChoice(Long id, Integer revision) {}
    // The legacy request hash serializes this record. Absent extensions stay absent.
    public record Adjustment(String request_key, Integer expected_revision, String action,
                             List<VoidLine> items, String discount_type, BigDecimal discount_value,
                             String discount_name, String reason,
                             @JsonInclude(JsonInclude.Include.NON_NULL) List<VoidAddon> addons,
                             @JsonInclude(JsonInclude.Include.NON_NULL) CouponChoice coupon) {}
    public record Preset(Long id, String name, String discount_type, BigDecimal value, Boolean enabled, Integer revision) {}
    @org.springframework.beans.factory.annotation.Autowired
    private com.restaurant.system.analytics.service.AnalyticsAggregationService analytics;
    @jakarta.persistence.PersistenceContext
    private jakarta.persistence.EntityManager entityManager;
    private final OrderRepository orders;
    private final OrderItemRepository items;
    private final OrderItemOptionRepository options;
    private final StoreRepository stores;
    private final AlloProductionPrintingPolicy policy;
    private final JdbcTemplate db;
    private final ObjectMapper json;
    public AlloSalesService(OrderRepository orders, OrderItemRepository items, OrderItemOptionRepository options, StoreRepository stores,
                           AlloProductionPrintingPolicy policy, JdbcTemplate db, ObjectMapper json) {
        this.orders=orders;this.items=items;this.options=options;this.stores=stores;this.policy=policy;this.db=db;this.json=json;
    }
    public boolean enabled(Long storeId) { return policy.allowsPadDirect(storeId); }
    public void requireEnabled(Long storeId) {
        if (!enabled(storeId)) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"ALLO_SALES_NOT_ENABLED");
    }
    public LocalDate today(Long storeId) {
        String zone = stores.findById(storeId).orElseThrow().timezone;
        return LocalDate.now(ZoneId.of(zone == null ? "America/Toronto" : zone));
    }
    public static LocalDate businessDate(Order o) { return o.submitted_at == null ? null : o.submitted_at.toLocalDate(); }
    public void recalculate(Order o, List<OrderItem> lines) {
        recalculate(o, lines, AlloMoney.optionsByItem(loadOptions(lines)));
    }

    private void recalculate(Order o, List<OrderItem> lines, Map<Long, List<OrderItemOption>> byItem) {
        BigDecimal gross = lines.stream().map(line -> AlloMoney.effectiveLine(line, byItem.getOrDefault(line.id, List.of())))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        var m=AlloMoney.calculate(gross,o.discount_type,o.discount_value,"completed".equals(o.status));
        o.subtotal_amount=m.gross();o.discount_amount=m.discount();o.tax_amount=m.tax();o.total_amount=m.total();
    }

    private List<OrderItemOption> loadOptions(List<OrderItem> lines) {
        return lines.isEmpty() ? List.of() : options.findAllByOrderItemIds(lines.stream().map(i -> i.id).toList());
    }

    public boolean hasFinancialAdjustment(Order order) {
        if (order.discount_type != null) return true;
        List<OrderItem> lines = items.findAllByOrderId(order.id);
        return lines.stream().anyMatch(i -> i.voided_quantity != null && i.voided_quantity > 0)
            || loadOptions(lines).stream().anyMatch(o -> o.voided_quantity > 0);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> recentOrders(Long store, LocalDate date, int page) {
        requireEnabled(store);
        LocalDate today = today(store);
        LocalDate earliest = today.minusDays(29);
        LocalDate selected = date == null ? today : date;
        if (selected.isBefore(earliest) || selected.isAfter(today) || page < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请选择最近 30 天的营业日期");
        }
        int pageSize = 50;
        var rows = db.queryForList("""
            select o.id, o.order_type, o.table_no, o.pickup_no, o.status,
                   to_char(o.submitted_at, 'YYYY-MM-DD HH24:MI:SS') as submitted_time,
                   case when o.status = 'cancelled' then 0 else o.total_amount end as total_amount,
                   case when o.status = 'cancelled' then 0 else o.discount_amount end as discount_amount,
                   exists (select 1 from allo_order_adjustments a where a.order_id = o.id
                       and a.store_id = o.store_id and a.action in ('VOID_ITEMS', 'VOID_ORDER')) as has_voids
            from orders o where o.store_id = ? and o.submitted_at >= ? and o.submitted_at < ?
                and o.status <> 'draft' and o.external_source is null and o.financial_mode is distinct from 'EXTERNAL_PLATFORM'
            order by o.submitted_at desc, o.id desc limit ? offset ?
            """, store, selected.atStartOfDay(), selected.plusDays(1).atStartOfDay(), pageSize + 1, (long) pageSize * page);
        return Map.of("date", selected, "earliest_date", earliest, "latest_date", today,
            "orders", rows.subList(0, Math.min(rows.size(), pageSize)), "has_more", rows.size() > pageSize);
    }

    private Map<String, Object> itemDetail(OrderItem item, List<OrderItemOption> choices, boolean cancelled) {
        int remaining = cancelled ? 0 : AlloMoney.effectiveQuantity(item);
        var row = new LinkedHashMap<String, Object>();
        row.put("id", item.id);
        row.put("quantity", item.quantity);
        row.put("voided_quantity", item.quantity - remaining);
        row.put("combo_role", item.combo_role);
        row.put("combo_group_no", item.combo_group_no);
        row.put("status", item.status);
        row.put("item_sku_snapshot", item.item_sku_snapshot);
        row.put("item_name_snapshot_zh", item.item_name_snapshot_zh);
        row.put("item_name_snapshot_en", item.item_name_snapshot_en);
        row.put("item_name_snapshot_fr", item.item_name_snapshot_fr);
        row.put("notes", item.notes);
        row.put("line_amount", item.line_amount);
        row.put("effective_line_amount", cancelled ? AlloMoney.cents(null) : AlloMoney.effectiveLine(item, choices));
        row.put("options", choices.stream().map(option -> {
            int original = AlloMoney.originalOptionQuantity(item, option);
            int active = cancelled ? 0 : AlloMoney.effectiveOptionQuantity(item, option);
            var choice = new LinkedHashMap<String, Object>();
            choice.put("id", option.id);
            choice.put("option_name_snapshot_zh", option.option_name_snapshot_zh);
            choice.put("option_name_snapshot_en", option.option_name_snapshot_en);
            choice.put("option_name_snapshot_fr", option.option_name_snapshot_fr);
            choice.put("quantity", original);
            choice.put("per_serving", option.quantity == null ? 1 : option.quantity);
            choice.put("voided_quantity", original - active);
            choice.put("is_addon", AlloMoney.canVoidOption(option));
            choice.put("standalone_voided_quantity", option.voided_quantity);
            return choice;
        }).toList());
        return row;
    }

    @Transactional(readOnly=true)
    public Map<String,Object> detail(Long id) {
        Order o=orders.findById(id).orElseThrow(); requireEnabled(o.store_id);
        List<OrderItem> lines = items.findAllByOrderId(id);
        var byItem = AlloMoney.optionsByItem(loadOptions(lines));
        boolean cancelled = "cancelled".equals(o.status);
        Map<String,Object> result=new LinkedHashMap<>();
        result.put("order", o);
        result.put("amounts", cancelled ? AlloMoney.calculate(BigDecimal.ZERO, null, null, false) : AlloMoney.determined(o));
        result.put("items", lines.stream().map(i -> itemDetail(i, byItem.getOrDefault(i.id, List.of()), cancelled)).toList());
        result.put("business_date", businessDate(o));
        result.put("editable", today(o.store_id).equals(businessDate(o)) && !o.kitchenMirror() && o.external_source == null
            && !Set.of("draft","cancelled").contains(o.status));
        result.put("adjustments", db.queryForList("""
            select a.*, to_char(a.created_at, 'YYYY-MM-DD HH24:MI:SS') as display_time,
                   o.order_no, o.order_type, o.table_no, o.pickup_no
            from allo_order_adjustments a join orders o on o.id=a.order_id
            where a.store_id=? and a.order_id=? order by a.id
            """, o.store_id, id));
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
        List<OrderItem> lines = items.findAllByOrderId(id);
        List<OrderItemOption> choices = loadOptions(lines);
        if (preview) {
            lines = lines.stream().map(i -> AlloCopies.copy(i, new OrderItem())).toList();
            choices = choices.stream().map(i -> AlloCopies.copy(i, new OrderItemOption())).toList();
        }
        List<Map<String,Object>> changes=new ArrayList<>();
        List<OrderItemOption> modifiedAddons = new ArrayList<>();
        if ("VOID_ORDER".equals(r.action()) || "VOID_ITEMS".equals(r.action())) {
            Map<Long,Integer> selected=new LinkedHashMap<>();
            if("VOID_ORDER".equals(r.action())) lines.stream().filter(i->AlloMoney.effectiveQuantity(i)>0 && !Set.of("combo_side","combo_egg").contains(i.combo_role==null?"":i.combo_role))
                .forEach(i->selected.put(i.id,AlloMoney.effectiveQuantity(i)));
            else {
                var requestedItems = r.items() == null ? List.<VoidLine>of() : r.items();
                var requestedAddons = r.addons() == null ? List.<VoidAddon>of() : r.addons();
                int totalSelections = requestedItems.size() + requestedAddons.size();
                if (totalSelections == 0 || totalSelections > 300) throw new BusinessException("VOID_ITEMS_REQUIRED");
                for (var v : requestedItems) {
                    if (v == null || v.item_id() == null || v.quantity() == null || v.quantity() < 1
                            || selected.put(v.item_id(), v.quantity()) != null) {
                        throw new BusinessException("VOID_QUANTITY_INVALID");
                    }
                }
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
            if ("VOID_ITEMS".equals(r.action()) && r.addons() != null) {
                voidAddons(r.addons(), lines, choices, changes, modifiedAddons);
            }
            if(lines.stream().noneMatch(i->AlloMoney.effectiveQuantity(i)>0))o.status="cancelled";
        } else if ("DISCOUNT".equals(r.action())) {
            AlloMoney.validateDiscount(r.discount_type(),r.discount_value());
            if(r.discount_name()==null||r.discount_name().isBlank()||r.discount_name().length()>120)throw new BusinessException("DISCOUNT_NAME_INVALID");
            o.discount_type=r.discount_type();o.discount_value=r.discount_value();o.discount_name=r.discount_name();
            o.discount_coupon_id = null;
        } else if ("APPLY_COUPON".equals(r.action())) {
            applyCoupon(o, r.coupon(), preview);
        } else if ("REMOVE_DISCOUNT".equals(r.action()) || "REMOVE_COUPON".equals(r.action())) {
            if ("REMOVE_COUPON".equals(r.action()) && o.discount_coupon_id == null) throw conflict("COUPON_NOT_APPLIED");
            o.discount_type = null;
            o.discount_value = null;
            o.discount_name = null;
            o.discount_coupon_id = null;
        }
        else throw new BusinessException("ADJUSTMENT_ACTION_INVALID");
        recalculate(o, lines, AlloMoney.optionsByItem(choices));
        o.current_revision=(o.current_revision==null?0:o.current_revision)+1;o.updated_at=LocalDateTime.now();
        var after=AlloMoney.determined(o);
        if(preview)return Map.of("before",before,"after",after,"items",changes,
            "discount_name", Objects.toString(o.discount_name, ""));
        items.saveAll(lines);
        if (!modifiedAddons.isEmpty()) options.saveAll(modifiedAddons);
        orders.saveAndFlush(o);
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
        var rule=new LinkedHashMap<String,Object>();rule.put("type",o.discount_type);rule.put("value",o.discount_value);rule.put("name",o.discount_name);
        rule.put("coupon_id", o.discount_coupon_id);
        snapshot.put("discount_rule",rule);
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
    private void voidAddons(List<VoidAddon> selected, List<OrderItem> lines, List<OrderItemOption> choices,
                            List<Map<String, Object>> changes, List<OrderItemOption> modified) {
        var byId = new HashMap<Long, OrderItem>();
        lines.forEach(line -> byId.put(line.id, line));
        var byOption = new HashMap<Long, OrderItemOption>();
        choices.forEach(option -> byOption.put(option.id, option));
        var used = new HashSet<Long>();
        for (var selection : selected) {
            if (selection == null || selection.option_id() == null || selection.quantity() == null
                    || selection.quantity() < 1 || !used.add(selection.option_id())) {
                throw new BusinessException("VOID_ADDON_QUANTITY_INVALID");
            }
            var option = byOption.get(selection.option_id());
            if (option == null || !AlloMoney.canVoidOption(option)) throw new BusinessException("VOID_ADDON_SCOPE_INVALID");
            var item = byId.get(option.order_item_id);
            if (selection.quantity() > AlloMoney.effectiveOptionQuantity(item, option)) {
                throw conflict("VOID_ADDON_QUANTITY_INVALID");
            }
            option.voided_quantity += selection.quantity();
            modified.add(option);
            String itemName = Objects.toString(item.item_name_snapshot_zh, item.item_name_snapshot_en);
            String optionName = Objects.toString(option.option_name_snapshot_zh, option.option_name_snapshot_en);
            changes.add(Map.of("item_id", item.id, "option_id", option.id, "name", itemName + " · " + optionName,
                "quantity", selection.quantity()));
        }
    }

    private void applyCoupon(Order order, CouponChoice coupon, boolean preview) {
        if (coupon == null || coupon.id() == null || coupon.revision() == null) {
            throw new BusinessException("COUPON_REQUIRED");
        }
        var rows = db.queryForList("""
            select id, name, discount_type, value from allo_discount_presets
            where store_id=? and kind='COUPON' and id=? and enabled and revision=?
            """ + (preview ? "" : " for share"), order.store_id, coupon.id(), coupon.revision());
        if (rows.isEmpty()) throw conflict("COUPON_UNAVAILABLE_OR_CHANGED");
        var row = rows.get(0);
        order.discount_coupon_id = coupon.id();
        order.discount_type = row.get("discount_type").toString();
        order.discount_value = (BigDecimal) row.get("value");
        order.discount_name = row.get("name").toString();
    }

    @Transactional(readOnly=true)
    public List<Map<String,Object>> presets(Long store, boolean coupons) {
        requireEnabled(store);
        return db.queryForList("select * from allo_discount_presets where store_id=? and kind=? order by id",
            store, coupons ? "COUPON" : "DISCOUNT");
    }

    @Transactional
    public List<Map<String,Object>> savePreset(Long store,Preset p,Long actor, boolean coupons) {
        requireEnabled(store);AlloMoney.validateDiscount(p.discount_type(),p.value());
        if(p.name()==null||p.name().isBlank()||p.name().length()>120)throw new BusinessException("DISCOUNT_NAME_INVALID");
        String kind = coupons ? "COUPON" : "DISCOUNT";
        if(p.id()==null)db.update("insert into allo_discount_presets(store_id,name,discount_type,value,enabled,created_by,created_at,updated_at,kind) values(?,?,?,?,?,?,?,?,?)",
            store,p.name().strip(),p.discount_type(),p.value(),p.enabled()==null||p.enabled(),actor,LocalDateTime.now(),LocalDateTime.now(),kind);
        else if(db.update("update allo_discount_presets set name=?,discount_type=?,value=?,enabled=?,revision=revision+1,updated_at=? where id=? and store_id=? and revision=? and kind=?",
            p.name().strip(),p.discount_type(),p.value(),!Boolean.FALSE.equals(p.enabled()),LocalDateTime.now(),p.id(),store,p.revision(),kind)!=1)throw conflict("PRESET_REVISION_STALE");
        return presets(store, coupons);
    }
    public String write(Object o) { try{return json.writeValueAsString(o);}catch(Exception e){throw new BusinessException("FINANCIAL_SNAPSHOT_INVALID");} }
    private String hash(String s) { try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);} }
    private static ResponseStatusException conflict(String s){return new ResponseStatusException(HttpStatus.CONFLICT,s);}
}
