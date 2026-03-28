package com.restaurant.system.order.repository;

import com.restaurant.system.order.entity.FrontdeskBeverageItem;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FrontdeskBeverageItemRepository extends JpaRepository<FrontdeskBeverageItem, Long> {

    @Query("""
        select fbi from FrontdeskBeverageItem fbi
        where fbi.store_id = :storeId
        order by fbi.created_at asc, fbi.id asc
        """)
    List<FrontdeskBeverageItem> findAllByStoreId(@Param("storeId") Long storeId);

    @Query("""
        select fbi from FrontdeskBeverageItem fbi
        where fbi.store_id = :storeId and fbi.status in :statuses
        order by fbi.created_at asc, fbi.id asc
        """)
    List<FrontdeskBeverageItem> findAllByStoreIdAndStatuses(
        @Param("storeId") Long storeId,
        @Param("statuses") List<String> statuses
    );

    @Query("""
        select fbi from FrontdeskBeverageItem fbi
        where fbi.order_id = :orderId
        order by fbi.created_at asc, fbi.id asc
        """)
    List<FrontdeskBeverageItem> findAllByOrderId(@Param("orderId") Long orderId);

    @Query("""
        select fbi from FrontdeskBeverageItem fbi
        where fbi.order_item_id = :orderItemId
        """)
    FrontdeskBeverageItem findByOrderItemId(@Param("orderItemId") Long orderItemId);
}
