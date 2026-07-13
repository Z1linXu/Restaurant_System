package com.restaurant.system.order.repository;

import com.restaurant.system.order.entity.OrderSubmissionRequest;
import java.time.LocalDateTime;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderSubmissionRequestRepository extends JpaRepository<OrderSubmissionRequest, Long> {

    @Modifying
    @Query(value = """
        insert into order_submission_requests (
            organization_id,
            store_id,
            idempotency_key,
            client_order_id,
            payload_hash,
            status,
            created_at,
            updated_at
        ) values (
            :organizationId,
            :storeId,
            :idempotencyKey,
            :clientOrderId,
            :payloadHash,
            'PROCESSING',
            :now,
            :now
        )
        on conflict (store_id, idempotency_key) do nothing
        """, nativeQuery = true)
    int insertIfAbsent(
        @Param("organizationId") Long organizationId,
        @Param("storeId") Long storeId,
        @Param("idempotencyKey") String idempotencyKey,
        @Param("clientOrderId") String clientOrderId,
        @Param("payloadHash") String payloadHash,
        @Param("now") LocalDateTime now
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        select request from OrderSubmissionRequest request
        where request.storeId = :storeId
          and request.idempotencyKey = :idempotencyKey
        """)
    Optional<OrderSubmissionRequest> findForUpdate(
        @Param("storeId") Long storeId,
        @Param("idempotencyKey") String idempotencyKey
    );
}
