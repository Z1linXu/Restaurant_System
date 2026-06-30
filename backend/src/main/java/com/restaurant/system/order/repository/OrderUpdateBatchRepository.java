package com.restaurant.system.order.repository;

import com.restaurant.system.order.entity.OrderUpdateBatch;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderUpdateBatchRepository extends JpaRepository<OrderUpdateBatch, Long> {

    @Query("""
        select batch from OrderUpdateBatch batch
        where batch.order_id = :orderId and batch.idempotency_key = :idempotencyKey
        """)
    Optional<OrderUpdateBatch> findByOrderIdAndIdempotencyKey(
        @Param("orderId") Long orderId,
        @Param("idempotencyKey") String idempotencyKey
    );
}
