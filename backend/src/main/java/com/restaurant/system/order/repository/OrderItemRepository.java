package com.restaurant.system.order.repository;

import com.restaurant.system.order.entity.OrderItem;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {

    @Query("select oi from OrderItem oi where oi.order_id = :orderId")
    List<OrderItem> findAllByOrderId(@Param("orderId") Long orderId);

    @Query("select oi from OrderItem oi where oi.order_id in :orderIds")
    List<OrderItem> findAllByOrderIds(@Param("orderIds") List<Long> orderIds);
}
