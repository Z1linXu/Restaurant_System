package com.restaurant.system.order.finish;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurant.system.audit.entity.AuditLog;
import com.restaurant.system.audit.repository.AuditLogRepository;
import com.restaurant.system.common.auth.AuthenticatedUser;
import com.restaurant.system.common.exception.BusinessException;
import com.restaurant.system.order.entity.Order;
import com.restaurant.system.order.repository.OrderRepository;
import com.restaurant.system.order.service.OrderService;
import com.restaurant.system.station.service.DiningTableDisplayService;
import com.restaurant.system.user.repository.StoreRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import static com.restaurant.system.order.finish.FinishAllTablesResponse.*;

/** A signed, read-only preview plus independently committed, durable per-order outcomes. */
@Service
public class FinishAllTablesService {
    private static final Set<String> ACTIVE = Set.of("submitted", "preparing", "ready");
    private final OrderRepository orders;
    private final StoreRepository stores;
    private final OrderService orderService;
    private final DiningTableDisplayService display;
    private final AuditLogRepository audits;
    private final EntityManager entityManager;
    private final JdbcTemplate db;
    private final ObjectMapper json;
    private final TransactionTemplate transaction;
    private final byte[] secret;

    public FinishAllTablesService(OrderRepository orders, StoreRepository stores, OrderService orderService,
            DiningTableDisplayService display, AuditLogRepository audits, EntityManager entityManager,
            JdbcTemplate db, ObjectMapper json, PlatformTransactionManager manager,
            @Value("${app.auth.jwt-secret}") String secret) {
        this.orders=orders; this.stores=stores; this.orderService=orderService; this.display=display;
        this.audits=audits; this.entityManager=entityManager; this.db=db; this.json=json;
        this.secret=secret.getBytes(StandardCharsets.UTF_8);
        transaction=new TransactionTemplate(manager);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        transaction.setTimeout(15);
    }

    @Transactional(readOnly = true)
    public Preview preview(Long storeId, AuthenticatedUser actor) {
        var store=stores.findById(storeId).orElseThrow(() -> new BusinessException("Store not found"));
        List<Order> open=openOrders(storeId);
        List<Target> targets=open.stream().filter(this::eligible).sorted(Comparator.comparing(o -> o.id))
            .map(o -> new Target(o.id,o.table_no,display.displayName(storeId,o.table_no),fingerprint(o))).toList();
        if (targets.size()>500) throw new BusinessException("Too many orders; finish some tables individually first");
        Set<String> blocked=new TreeSet<>();
        open.stream().filter(o -> !eligible(o)).forEach(o -> blocked.add(display.displayName(storeId,o.table_no)));
        Plan plan=new Plan(UUID.randomUUID().toString(),storeId,actor.userId(),Instant.now().plusSeconds(600).getEpochSecond(),targets);
        long slots=targets.stream().map(Target::table_no).distinct().filter(t -> open.stream()
            .filter(o -> !eligible(o)).noneMatch(o -> display.contextsOverlap(storeId,t,o.table_no))).count();
        return new Preview(plan.batch_id(),storeId,store.name,plan.expires_at(),targets,targets.size(),(int)slots,List.copyOf(blocked),sign(plan));
    }

    public Result execute(Long storeId, AuthenticatedUser actor, String token) {
        Plan plan=verify(token);
        if (!storeId.equals(plan.store_id()) || !actor.userId().equals(plan.actor_user_id()))
            throw new BusinessException("FINISH_PREVIEW_SCOPE_MISMATCH");
        String encoded=encode(plan);
        transaction.executeWithoutResult(status -> {
            var existing=db.queryForList("select plan_json from order_finish_batches where id=?",String.class,plan.batch_id());
            if (existing.isEmpty() && plan.expires_at()<Instant.now().getEpochSecond())
                throw new BusinessException("FINISH_PREVIEW_EXPIRED: Refresh the affected order list / 请重新确认桌台列表");
            db.update("insert into order_finish_batches(id,store_id,actor_user_id,plan_json) values (?,?,?,?) on conflict(id) do nothing",
                plan.batch_id(),storeId,actor.userId(),encoded);
            String stored=db.queryForObject("select plan_json from order_finish_batches where id=?",String.class,plan.batch_id());
            if (!encoded.equals(stored)) throw new BusinessException("FINISH_BATCH_MISMATCH");
        });
        for (Target target:plan.orders()) {
            try {
                transaction.executeWithoutResult(status -> process(plan,target,actor));
            } catch (RuntimeException failure) {
                // A rolled-back order cannot roll back previously committed orders. No exception/SQL data is exposed.
                transaction.executeWithoutResult(status -> {
                    List<Outcome> results=lockedResults(plan.batch_id());
                    if (results.stream().noneMatch(r -> r.order_id().equals(target.order_id()))) {
                        results.add(new Outcome(target.order_id(),target.table_no(),"failed","ORDER_FINISH_FAILED: retry this order individually / 请检查后单独结束"));
                        saveResults(plan,results);
                        audit(actor,plan,"TABLE_FINISH_FAILED",target.order_id(),results.get(results.size()-1));
                    }
                });
            }
        }
        return transaction.execute(status -> {
            List<Outcome> results=lockedResults(plan.batch_id());
            Result result=result(plan,results);
            var finished=db.queryForObject("select finished_at is not null from order_finish_batches where id=?",Boolean.class,plan.batch_id());
            if (!Boolean.TRUE.equals(finished)) {
                audit(actor,plan,"TABLES_FINISHED",null,result);
                db.update("update order_finish_batches set finished_at=current_timestamp where id=?",plan.batch_id());
            }
            return result;
        });
    }

    private void process(Plan plan, Target target, AuthenticatedUser actor) {
        List<Outcome> results=lockedResults(plan.batch_id());
        if (results.stream().anyMatch(r -> r.order_id().equals(target.order_id()))) return;
        Order order=orders.findByIdForUpdate(target.order_id());
        if (order!=null) entityManager.refresh(order,LockModeType.PESSIMISTIC_WRITE);
        String reason=null;
        if (order==null || !plan.store_id().equals(order.store_id)) reason="ORDER_STORE_CHANGED";
        else if (!eligible(order)) reason="ORDER_NO_LONGER_ELIGIBLE: "+order.status;
        else if (!fingerprint(order).equals(target.fingerprint())) reason="ORDER_CHANGED_SINCE_PREVIEW: Review changes / 订单已更新，请重新确认";
        if (reason==null) orderService.completeOrder(order.id);
        Outcome outcome=new Outcome(target.order_id(),target.table_no(),reason==null?"completed":"skipped",reason);
        results.add(outcome);
        saveResults(plan,results);
        audit(actor,plan,"TABLE_FINISH_RESULT",target.order_id(),outcome);
    }

    private List<Outcome> lockedResults(String id) {
        String value=db.queryForObject("select results_json from order_finish_batches where id=? for update",String.class,id);
        try { return new ArrayList<>(json.readValue(value,new TypeReference<List<Outcome>>() {})); }
        catch (Exception e) { throw new IllegalStateException("Invalid stored Finish results"); }
    }
    private void saveResults(Plan plan,List<Outcome> results) {
        db.update("update order_finish_batches set results_json=? where id=?",encode(results),plan.batch_id());
    }
    private Result result(Plan plan,List<Outcome> results) {
        List<Order> open=openOrders(plan.store_id());
        int released=(int)plan.orders().stream().map(Target::table_no).distinct()
            .filter(t -> open.stream().noneMatch(o -> display.contextsOverlap(plan.store_id(),t,o.table_no))).count();
        List<String> remaining=open.stream().map(o -> display.displayName(plan.store_id(),o.table_no)).distinct().sorted().toList();
        return new Result(plan.batch_id(),(int)results.stream().filter(o -> "completed".equals(o.outcome())).count(),
            (int)results.stream().filter(o -> "skipped".equals(o.outcome())).count(),
            (int)results.stream().filter(o -> "failed".equals(o.outcome())).count(),released,List.copyOf(results),remaining);
    }
    private List<Order> openOrders(Long storeId) {
        return orders.findAllByStoreId(storeId).stream().filter(this::dining)
            .filter(o -> !Set.of("completed","cancelled").contains(o.status==null?"":o.status)).toList();
    }
    private boolean dining(Order o) {
        return "dine_in".equals(o.order_type) && o.table_no!=null && !o.table_no.isBlank()
            && (o.financial_mode==null || "IN_STORE".equals(o.financial_mode))
            && (o.external_source==null || o.external_source.isBlank());
    }
    private boolean eligible(Order o) { return dining(o) && ACTIVE.contains(o.status==null?"":o.status) && o.completed_at==null; }
    private String fingerprint(Order o) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(digest(encode(Arrays.asList(
            o.id,o.store_id,o.order_type,o.table_no,o.current_revision,o.submitted_at==null?null:o.submitted_at.toString(),
            o.total_amount==null?null:o.total_amount.stripTrailingZeros().toPlainString())).getBytes(StandardCharsets.UTF_8)));
    }
    private void audit(AuthenticatedUser actor,Plan plan,String action,Long id,Object metadata) {
        AuditLog log=new AuditLog(); log.store_id=plan.store_id(); log.actor_user_id=actor.userId();
        log.actor_name_snapshot=actor.fullName()==null?actor.username():actor.fullName(); log.actor_role_snapshot=actor.roleCode();
        log.action=action; log.entity_type="ORDER_FINISH_BATCH"; log.entity_id=id;
        log.summary="Finish All Tables / 结束所有桌台"; log.created_at=LocalDateTime.now();
        log.metadata_json=encode(Map.of("batch_id",plan.batch_id(),"store_id",plan.store_id(),"details",metadata));
        audits.saveAndFlush(log); // Required audit and order result commit together.
    }
    private String sign(Plan plan) {
        String payload=Base64.getUrlEncoder().withoutPadding().encodeToString(encode(plan).getBytes(StandardCharsets.UTF_8));
        return payload+"."+Base64.getUrlEncoder().withoutPadding().encodeToString(mac(payload));
    }
    private Plan verify(String token) {
        try {
            if (token==null || token.length()>250_000) throw new IllegalArgumentException();
            String[] parts=token.split("\\.",-1);
            if (parts.length!=2 || !MessageDigest.isEqual(mac(parts[0]),Base64.getUrlDecoder().decode(parts[1]))) throw new IllegalArgumentException();
            Plan plan=json.readValue(Base64.getUrlDecoder().decode(parts[0]),Plan.class);
            UUID.fromString(plan.batch_id());
            if (plan.orders()==null || plan.orders().size()>500 || plan.orders().stream().map(Target::order_id).distinct().count()!=plan.orders().size()) throw new IllegalArgumentException();
            return plan;
        } catch (Exception e) { throw new BusinessException("FINISH_PREVIEW_INVALID: Reload the preview / 请重新加载确认列表"); }
    }
    private byte[] mac(String payload) {
        try { Mac mac=Mac.getInstance("HmacSHA256"); mac.init(new SecretKeySpec(secret,"HmacSHA256"));
            return mac.doFinal(("finish-all-tables-v1:"+payload).getBytes(StandardCharsets.UTF_8)); }
        catch (Exception e) { throw new IllegalStateException("Could not sign Finish preview"); }
    }
    private byte[] digest(byte[] input) {
        try { return MessageDigest.getInstance("SHA-256").digest(input); }
        catch (Exception e) { throw new IllegalStateException(e); }
    }
    private String encode(Object value) {
        try { return json.writeValueAsString(value); }
        catch (Exception e) { throw new IllegalStateException("Could not serialize Finish data"); }
    }
}
