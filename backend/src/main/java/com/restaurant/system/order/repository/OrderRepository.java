package com.restaurant.system.order.repository;

import com.restaurant.system.order.entity.Order;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderRepository extends JpaRepository<Order, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from Order o where o.id = :id")
    Order findByIdForUpdate(@Param("id") Long id);

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
              and status in ('draft', 'submitted', 'preparing', 'ready')
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
              and status in ('draft', 'submitted', 'preparing', 'ready')
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
              and (
                (created_at is not null and created_at >= :startAt and created_at < :endAt)
                or (submitted_at is not null and submitted_at >= :startAt and submitted_at < :endAt)
                or (completed_at is not null and completed_at >= :startAt and completed_at < :endAt)
                or (updated_at is not null and updated_at >= :startAt and updated_at < :endAt)
              )
            order by
              case when updated_at is null then 1 else 0 end,
              updated_at desc,
              case when submitted_at is null then 1 else 0 end,
              submitted_at desc,
              created_at desc,
              id desc
            """,
        nativeQuery = true
    )
    List<Order> findTodayByStoreId(
        @Param("storeId") Long storeId,
        @Param("startAt") LocalDateTime startAt,
        @Param("endAt") LocalDateTime endAt,
        Pageable pageable
    );

    @Query(
        value = """
            select count(*)
            from orders
            where store_id = :storeId
              and (
                (created_at is not null and created_at >= :startAt and created_at < :endAt)
                or (submitted_at is not null and submitted_at >= :startAt and submitted_at < :endAt)
                or (completed_at is not null and completed_at >= :startAt and completed_at < :endAt)
                or (updated_at is not null and updated_at >= :startAt and updated_at < :endAt)
              )
            """,
        nativeQuery = true
    )
    long countTodayByStoreId(
        @Param("storeId") Long storeId,
        @Param("startAt") LocalDateTime startAt,
        @Param("endAt") LocalDateTime endAt
    );

    @Query(
        value = """
            select coalesce(sum(total_amount), 0)
            from orders
            where store_id = :storeId
              and status = 'completed'
              and completed_at is not null
              and completed_at >= :startAt
              and completed_at < :endAt
            """,
        nativeQuery = true
    )
    java.math.BigDecimal sumCompletedTotalByStoreIdAndCompletedAtBetween(
        @Param("storeId") Long storeId,
        @Param("startAt") LocalDateTime startAt,
        @Param("endAt") LocalDateTime endAt
    );

    @Query(
        value = """
            select count(*)
            from orders
            where store_id = :storeId
              and status in ('submitted', 'preparing', 'ready')
            """,
        nativeQuery = true
    )
    long countActiveByStoreId(@Param("storeId") Long storeId);

    @Query(
        value = """
            select count(distinct table_no)
            from orders
            where store_id = :storeId
              and order_type = 'dine_in'
              and table_no is not null
              and status in ('draft', 'submitted', 'preparing', 'ready')
            """,
        nativeQuery = true
    )
    long countOccupiedDineInTablesByStoreId(@Param("storeId") Long storeId);

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
