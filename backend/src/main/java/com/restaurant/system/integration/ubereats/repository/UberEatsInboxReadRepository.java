package com.restaurant.system.integration.ubereats.repository;

import com.restaurant.system.integration.ubereats.entity.UberEatsOrder;
import com.restaurant.system.integration.ubereats.service.UberEatsInboxDay;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.*;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** Inbox-only reads. No print entities, ticket payloads, locks, or operational writes. */
@Repository
public class UberEatsInboxReadRepository {
    public record Job(Long orderId, String module, String status) {}
    public record Dispatch(String sourceKey, String status) {}
    public record Page(List<UberEatsOrder> rows, boolean hasMore, Long nextBeforeId) {}
    private final NamedParameterJdbcTemplate db;

    public UberEatsInboxReadRepository(NamedParameterJdbcTemplate db) { this.db = db; }

    public List<Job> latestJobs(Long storeId, Collection<Long> orderIds) {
        if (orderIds.isEmpty()) return List.of();
        return db.query("""
            SELECT order_id,module_code,status FROM (
              SELECT order_id,module_code,status,
                row_number() OVER (PARTITION BY order_id,module_code ORDER BY created_at DESC,id DESC) AS position
              FROM print_jobs WHERE store_id=:store AND order_id IN (:orders)
                AND module_code IN ('GRAB','HOT_KITCHEN')
            ) latest WHERE position=1
            """, Map.of("store",storeId,"orders",orderIds),
            (r,n)->new Job(r.getLong("order_id"),r.getString("module_code"),r.getString("status")));
    }

    public List<Dispatch> dispatches(Long storeId, Collection<String> sourceKeys) {
        if (sourceKeys.isEmpty()) return List.of();
        return db.query("SELECT source_key,status FROM order_dispatch_outbox WHERE store_id=:store AND source_key IN (:keys)",
            Map.of("store",storeId,"keys",sourceKeys),
            (r,n)->new Dispatch(r.getString("source_key"),r.getString("status")));
    }

    private static final String COLUMNS = """
        o.id,o.store_id,o.status,o.display_id,o.uber_order_id,o.mapping_status,o.mapping_error,o.last_error,
        o.local_order_id,o.placed_at,o.created_at,o.accepted_at,o.cancelled_at,o.raw_order_snapshot_json,
        o.processing_mode,o.customer_display_name,o.released_at,o.accepted_observed_at,o.local_request_json,
        o.kitchen_dispatched_at,o.cancelled,o.edit_required
        """;
    private static final String ON_DAY = """
        ((coalesce(o.kitchen_dispatched_at,o.accepted_observed_at,o.released_at,o.placed_at)>=:start
          AND coalesce(o.kitchen_dispatched_at,o.accepted_observed_at,o.released_at,o.placed_at)<:end)
         OR (o.kitchen_dispatched_at IS NULL AND o.accepted_observed_at IS NULL AND o.released_at IS NULL
          AND o.placed_at IS NULL AND o.created_at>=:createdStart AND o.created_at<:createdEnd))
        """;
    private static final String WARNINGS = """
        (o.status IN ('ACCEPTANCE_REVIEW_REQUIRED','RELEASED_MAPPING_REQUIRED','MIRROR_LOCAL_FAILED',
          'LOCAL_REVIEW_REQUIRED','EDIT_REVIEW_REQUIRED','EXTERNAL_STATE_REVIEW_REQUIRED',
          'CANCELLED_AFTER_RELEASE','KITCHEN_SENT_WITH_MAPPING_WARNINGS','LOCAL_FAILED','MAPPING_REQUIRED',
          'DECISION_REVIEW_REQUIRED','CANCELLED_REVIEW_REQUIRED','SCHEDULED_REVIEW_REQUIRED','CUTOVER_REVIEW_REQUIRED')
          OR o.status LIKE '%REVIEW_REQUIRED' OR o.last_error IS NOT NULL
          OR o.status NOT IN ('RECEIVED','PENDING','ACCEPTING','DENYING','UBER_ACCEPTED','ACCEPTED','DENIED','CANCELLED',
            'WAITING_FOR_ACCEPTANCE','WAITING_FOR_RELEASE','MIRROR_READY','RELEASED_TO_KITCHEN','PRINTED')
          OR (o.status IN ('RELEASED_TO_KITCHEN','ACCEPTED') AND o.local_order_id IS NULL)
          OR (o.local_order_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM orders local
            WHERE local.id=o.local_order_id AND local.store_id=:store))
          OR o.edit_required=true OR (o.cancelled=true AND o.local_order_id IS NOT NULL))
        """;
    // One narrow scan/set projection, rather than a correlated full print-table query per Uber row.
    private static final String LATEST_JOBS = """
        WITH latest_jobs AS MATERIALIZED (
          SELECT DISTINCT ON (order_id,module_code) order_id,module_code,status
          FROM print_jobs WHERE store_id=:store AND module_code IN ('GRAB','HOT_KITCHEN')
          ORDER BY order_id,module_code,created_at DESC,id DESC
        ), job_states AS (
          SELECT order_id,bool_or(module_code='GRAB') AS has_grab,bool_or(status<>'PRINTED') AS unfinished,
            bool_or(status IN ('FAILED','UNCERTAIN','CANCELLED','POLICY_BLOCKED','CAPABILITY_UNAVAILABLE','SKIPPED')) AS has_exception
          FROM latest_jobs GROUP BY order_id
        ), fallback_states AS (
          SELECT regexp_replace(e.source_key,':(GRAB|HOT_KITCHEN)$','') AS source_order_key,
            bool_or(e.status IN ('FAILED','UNCERTAIN','CANCELLED','POLICY_BLOCKED','CAPABILITY_UNAVAILABLE','SKIPPED')) AS has_exception
          FROM order_dispatch_outbox e LEFT JOIN latest_jobs p
            ON e.source_key='submit:'||p.order_id||':'||p.module_code
          WHERE e.store_id=:store AND (e.source_key LIKE 'submit:%:GRAB' OR e.source_key LIKE 'submit:%:HOT_KITCHEN')
            AND p.order_id IS NULL
          GROUP BY regexp_replace(e.source_key,':(GRAB|HOT_KITCHEN)$','')
        )
        """;
    private static final String ATTENTION_JOINS = """
        LEFT JOIN job_states j ON j.order_id=o.local_order_id
        LEFT JOIN fallback_states f ON f.source_order_key='submit:'||o.local_order_id
        """;
    private static final String PRINT_EXCEPTION = """
        (o.processing_mode='KITCHEN_MIRROR' AND (coalesce(j.has_exception,false) OR coalesce(f.has_exception,false)))
        """;
    private static final String UNFINISHED = """
        ((o.local_order_id IS NULL AND o.status NOT IN ('ACCEPTED','DENIED','CANCELLED','PRINTED'))
         OR (o.local_order_id IS NOT NULL AND EXISTS (SELECT 1 FROM orders local
           WHERE local.id=o.local_order_id AND local.store_id=:store
           AND lower(local.status) NOT IN ('completed','cancelled')))
         OR (o.processing_mode='KITCHEN_MIRROR' AND o.local_order_id IS NOT NULL AND
           (NOT coalesce(j.has_grab,false) OR coalesce(j.unfinished,false) OR f.source_order_key IS NOT NULL)))
        """;

    public Page page(String environment, Long store, UberEatsInboxDay day, String bucket, Long before, int limit) {
        if (!Set.of("today","attention","history").contains(bucket)) throw new IllegalArgumentException("Unknown inbox bucket");
        Map<String,Object> parameters=new HashMap<>();
        parameters.put("env",environment); parameters.put("store",store);
        parameters.put("start",day.start()); parameters.put("end",day.end());
        parameters.put("createdStart",day.createdStart()); parameters.put("createdEnd",day.createdEnd());
        parameters.put("limit",limit+1);
        String predicate=ON_DAY;
        String prefix="",joins="";
        if ("attention".equals(bucket)) {
            prefix=LATEST_JOBS; joins=ATTENTION_JOINS;
            predicate="("+WARNINGS+" OR "+PRINT_EXCEPTION+" OR (("+ON_DAY+") IS NOT TRUE AND "+UNFINISHED+"))";
        }
        String cursor="";
        if (before!=null) { cursor=" AND o.id<:before"; parameters.put("before",before); }
        List<UberEatsOrder> result=db.query(prefix+"SELECT "+COLUMNS+" FROM uber_eats_orders o "+joins+" WHERE o.environment=:env AND o.store_id=:store AND "+predicate+cursor+" ORDER BY o.id DESC LIMIT :limit", parameters,
            (r,n)->map(r));
        boolean more=result.size()>limit;
        List<UberEatsOrder> rows=more?List.copyOf(result.subList(0,limit)):List.copyOf(result);
        return new Page(rows,more,more?rows.get(rows.size()-1).id:null);
    }

    private static LocalDateTime time(ResultSet r,String name) throws SQLException {
        var value=r.getTimestamp(name); return value==null?null:value.toLocalDateTime();
    }
    private static UberEatsOrder map(ResultSet r) throws SQLException {
        var o=new UberEatsOrder(); o.id=r.getLong("id"); o.storeId=r.getLong("store_id");
        o.status=r.getString("status"); o.displayId=r.getString("display_id"); o.uberOrderId=r.getString("uber_order_id");
        o.mappingStatus=r.getString("mapping_status"); o.mappingError=r.getString("mapping_error"); o.lastError=r.getString("last_error");
        o.localOrderId=r.getObject("local_order_id",Long.class); o.placedAt=time(r,"placed_at"); o.createdAt=time(r,"created_at");
        o.acceptedAt=time(r,"accepted_at"); o.cancelledAt=time(r,"cancelled_at"); o.rawOrderSnapshotJson=r.getString("raw_order_snapshot_json");
        o.processingMode=r.getString("processing_mode"); o.customerDisplayName=r.getString("customer_display_name"); o.releasedAt=time(r,"released_at");
        o.acceptedObservedAt=time(r,"accepted_observed_at"); o.localRequestJson=r.getString("local_request_json");
        o.kitchenDispatchedAt=time(r,"kitchen_dispatched_at"); o.cancelled=r.getBoolean("cancelled"); o.editRequired=r.getBoolean("edit_required");
        return o;
    }
}
