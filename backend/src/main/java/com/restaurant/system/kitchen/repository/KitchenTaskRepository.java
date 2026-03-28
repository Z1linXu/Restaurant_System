package com.restaurant.system.kitchen.repository;

import com.restaurant.system.kitchen.entity.KitchenTask;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface KitchenTaskRepository extends JpaRepository<KitchenTask, Long> {

    @Query("select kt from KitchenTask kt where kt.store_id = :storeId")
    List<KitchenTask> findAllByStoreId(@Param("storeId") Long storeId);

    @Query("""
        select kt from KitchenTask kt
        where kt.store_id = :storeId and kt.station_code = :stationCode
        """)
    List<KitchenTask> findAllByStoreIdAndStationCode(
        @Param("storeId") Long storeId,
        @Param("stationCode") String stationCode
    );

    @Query("""
        select count(kt) from KitchenTask kt
        where kt.order_id = :orderId and kt.status in ('pending', 'in_progress')
        """)
    long countOpenTasksByOrderId(@Param("orderId") Long orderId);

    @Query("""
        select kt from KitchenTask kt
        where kt.store_id = :storeId and kt.status = 'ready_for_pickup'
        order by kt.completed_at asc, kt.id asc
        """)
    List<KitchenTask> findShelfTasksByStoreId(@Param("storeId") Long storeId);

    @Query("""
        select kt from KitchenTask kt
        where kt.store_id = :storeId and kt.status in ('pending', 'in_progress')
        order by kt.created_at asc, kt.id asc
        """)
    List<KitchenTask> findActiveTasksByStoreId(@Param("storeId") Long storeId);

    @Query("""
        select kt from KitchenTask kt
        where kt.store_id = :storeId and kt.station_code in :stationCodes and kt.status in ('pending', 'in_progress')
        order by kt.created_at asc, kt.id asc
        """)
    List<KitchenTask> findActiveTasksByStoreIdAndStationCodes(
        @Param("storeId") Long storeId,
        @Param("stationCodes") List<String> stationCodes
    );

    @Query("""
        select kt from KitchenTask kt
        where kt.order_id in :orderIds
        order by kt.created_at asc, kt.id asc
        """)
    List<KitchenTask> findAllByOrderIds(@Param("orderIds") List<Long> orderIds);

    @Query("""
        select kt from KitchenTask kt
        where kt.order_id = :orderId
        order by kt.created_at asc, kt.id asc
        """)
    List<KitchenTask> findAllByOrderId(@Param("orderId") Long orderId);
}
