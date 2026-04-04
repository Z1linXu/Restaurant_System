package com.restaurant.system.order.repository;

import com.restaurant.system.order.entity.OrderItemOption;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderItemOptionRepository extends JpaRepository<OrderItemOption, Long> {

    @Query(
        value = """
            select *
            from order_item_options
            where order_item_id in (:orderItemIds)
            order by id asc
            """,
        nativeQuery = true
    )
    List<OrderItemOption> findAllByOrderItemIds(@Param("orderItemIds") List<Long> orderItemIds);

    @Modifying
    @Query(
        value = """
            delete from order_item_options
            where order_item_id = :orderItemId
            """,
        nativeQuery = true
    )
    void deleteByOrderItemId(@Param("orderItemId") Long orderItemId);
}
