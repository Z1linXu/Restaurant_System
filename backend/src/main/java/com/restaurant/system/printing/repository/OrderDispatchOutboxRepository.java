package com.restaurant.system.printing.repository;

import com.restaurant.system.printing.entity.OrderDispatchOutbox;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

public interface OrderDispatchOutboxRepository extends JpaRepository<OrderDispatchOutbox, Long> {

    Optional<OrderDispatchOutbox> findBySourceKey(String sourceKey);

    @Modifying
    @Query(value = """
        insert into order_dispatch_outbox (
            organization_id,
            store_id,
            order_id,
            order_update_batch_id,
            module_code,
            event_type,
            source_key,
            status,
            attempt_count,
            next_attempt_at,
            created_at,
            updated_at
        ) values (
            :organizationId,
            :storeId,
            :orderId,
            :orderUpdateBatchId,
            :moduleCode,
            :eventType,
            :sourceKey,
            'PENDING',
            0,
            :now,
            :now,
            :now
        )
        on conflict (source_key) do nothing
        """, nativeQuery = true)
    int insertIfAbsent(
        @Param("organizationId") Long organizationId,
        @Param("storeId") Long storeId,
        @Param("orderId") Long orderId,
        @Param("orderUpdateBatchId") Long orderUpdateBatchId,
        @Param("moduleCode") String moduleCode,
        @Param("eventType") String eventType,
        @Param("sourceKey") String sourceKey,
        @Param("now") LocalDateTime now
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        select event from OrderDispatchOutbox event
        where event.status = 'PENDING'
          and event.nextAttemptAt <= :now
        order by event.id asc
        """)
    List<OrderDispatchOutbox> findDueForUpdate(@Param("now") LocalDateTime now, Pageable pageable);
}
