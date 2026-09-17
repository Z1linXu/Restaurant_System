package com.restaurant.system.integration.ubereats.repository;

import com.restaurant.system.integration.ubereats.entity.UberEatsEvent;

import jakarta.persistence.LockModeType;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.*;

public interface UberEatsEventRepository extends JpaRepository<UberEatsEvent, Long> {
    boolean existsByEnvironmentAndUberStoreIdAndUberOrderIdAndEventType(
            String environment, String uberStoreId, String uberOrderId, String eventType);

    Optional<UberEatsEvent> findByEnvironmentAndEventId(String environment, String eventId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from UberEatsEvent e where e.id = :id")
    Optional<UberEatsEvent> lock(@Param("id") Long id);

    @Query(
            "select e from UberEatsEvent e where e.environment = :env and e.status = 'PENDING' and"
                    + " e.nextAttemptAt <= :now order by e.id")
    List<UberEatsEvent> due(
            @Param("env") String env, @Param("now") LocalDateTime now, Pageable page);

    Optional<UberEatsEvent> findFirstByEnvironmentAndUberStoreIdOrderByIdDesc(
            String environment, String uberStoreId);

    @Modifying
    @Query(
            value =
                    """
                    insert into uber_eats_events(environment,event_id,event_type,uber_store_id,uber_order_id,body_hash,status,attempt_count,next_attempt_at,created_at,updated_at)
                    values (:env,:eventId,:type,:store,:orderId,:hash,:status,0,:now,:now,:now)
                    on conflict(environment,event_id) do nothing
                    """,
            nativeQuery = true)
    int insertIfAbsent(
            @Param("env") String env,
            @Param("eventId") String eventId,
            @Param("type") String type,
            @Param("store") String store,
            @Param("orderId") String orderId,
            @Param("hash") String hash,
            @Param("status") String status,
            @Param("now") LocalDateTime now);
}
