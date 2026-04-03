package com.restaurant.system.order.repository;

import com.restaurant.system.order.entity.OrderItem;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {

    @Query(
        value = """
            select *
            from order_items
            where id = :id
            """,
        nativeQuery = true
    )
    OrderItem findExistingById(@Param("id") Long id);

    @Query(
        value = """
            select *
            from order_items
            where order_id = :orderId
            order by created_at asc, id asc
            """,
        nativeQuery = true
    )
    List<OrderItem> findAllByOrderId(@Param("orderId") Long orderId);

    @Query(
        value = """
            select *
            from order_items
            where order_id in (:orderIds)
            order by created_at asc, id asc
            """,
        nativeQuery = true
    )
    List<OrderItem> findAllByOrderIds(@Param("orderIds") List<Long> orderIds);
}
