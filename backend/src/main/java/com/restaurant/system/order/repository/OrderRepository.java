package com.restaurant.system.order.repository;

import com.restaurant.system.order.entity.Order;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderRepository extends JpaRepository<Order, Long> {

    @Query("""
        select o from Order o
        where o.store_id = :storeId and o.ready_at is not null
        order by coalesce(o.completed_at, o.ready_at) desc
        """)
    List<Order> findRecentFinishedOrders(@Param("storeId") Long storeId, Pageable pageable);

    @Query("""
        select o from Order o
        where o.store_id = :storeId and o.status in ('submitted', 'preparing', 'ready', 'picked_up')
        order by o.created_at desc
        """)
    List<Order> findActiveOperationalOrders(@Param("storeId") Long storeId);

    @Query("""
        select o from Order o
        where o.store_id = :storeId
        order by o.updated_at desc, o.id desc
        """)
    List<Order> findAllByStoreId(@Param("storeId") Long storeId);
}
