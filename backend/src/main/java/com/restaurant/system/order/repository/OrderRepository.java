package com.restaurant.system.order.repository;

import com.restaurant.system.order.entity.Order;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderRepository extends JpaRepository<Order, Long> {

    @Query(
        value = """
            select *
            from orders
            where id = :id
            """,
        nativeQuery = true
    )
    Order findExistingById(@Param("id") Long id);

    @Query(
        value = """
            select *
            from orders
            where store_id = :storeId
              and table_no = :tableNo
              and status = 'draft'
            order by updated_at desc, id desc
            limit 1
            """,
        nativeQuery = true
    )
    Order findLatestDraftByStoreIdAndTableNo(@Param("storeId") Long storeId, @Param("tableNo") String tableNo);

    @Query(
        value = """
            select *
            from orders
            where store_id = :storeId
              and pickup_no = :pickupNo
              and status = 'draft'
            order by updated_at desc, id desc
            limit 1
            """,
        nativeQuery = true
    )
    Order findLatestDraftByStoreIdAndPickupNo(@Param("storeId") Long storeId, @Param("pickupNo") String pickupNo);

    @Query(
        value = """
            select *
            from orders
            where store_id = :storeId
              and table_no = :tableNo
              and status in ('draft', 'submitted', 'preparing')
            order by updated_at desc, id desc
            limit 1
            """,
        nativeQuery = true
    )
    Order findLatestEditableByStoreIdAndTableNo(@Param("storeId") Long storeId, @Param("tableNo") String tableNo);

    @Query(
        value = """
            select *
            from orders
            where store_id = :storeId
              and pickup_no = :pickupNo
              and status in ('draft', 'submitted', 'preparing')
            order by updated_at desc, id desc
            limit 1
            """,
        nativeQuery = true
    )
    Order findLatestEditableByStoreIdAndPickupNo(@Param("storeId") Long storeId, @Param("pickupNo") String pickupNo);

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

    @Query(
        value = """
            select *
            from orders
            where store_id = :storeId
              and status = 'completed'
              and completed_at is not null
              and date(completed_at) = :summaryDate
            order by completed_at asc, id asc
            """,
        nativeQuery = true
    )
    List<Order> findCompletedByStoreIdAndCompletedDate(@Param("storeId") Long storeId, @Param("summaryDate") java.sql.Date summaryDate);

    @Query(
        value = """
            select *
            from orders
            where store_id = :storeId
              and status = 'cancelled'
              and updated_at is not null
              and date(updated_at) = :summaryDate
            order by updated_at asc, id asc
            """,
        nativeQuery = true
    )
    List<Order> findCancelledByStoreIdAndUpdatedDate(@Param("storeId") Long storeId, @Param("summaryDate") java.sql.Date summaryDate);
}
