package com.restaurant.system.printing.repository;

import com.restaurant.system.printing.entity.PrintJob;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PrintJobRepository extends JpaRepository<PrintJob, Long> {

    java.util.Optional<PrintJob> findByDispatchSourceKey(String dispatchSourceKey);

    @Query("""
        select pj from PrintJob pj
        where pj.store_id = :storeId
          and pj.created_at >= :startAt
          and pj.created_at < :endAt
        order by pj.created_at desc, pj.id desc
        """)
    List<PrintJob> findAllInCreatedRange(
        @Param("storeId") Long storeId,
        @Param("startAt") LocalDateTime startAt,
        @Param("endAt") LocalDateTime endAt
    );

    @Query("""
        select pj from PrintJob pj
        where pj.store_id = :storeId
        order by pj.created_at desc, pj.id desc
        """)
    List<PrintJob> findRecentByStoreId(@Param("storeId") Long storeId, Pageable pageable);

    @Query("""
        select pj from PrintJob pj
        where pj.store_id = :storeId and pj.order_id = :orderId
        order by pj.created_at desc, pj.id desc
        """)
    List<PrintJob> findAllByStoreIdAndOrderId(
        @Param("storeId") Long storeId,
        @Param("orderId") Long orderId
    );

    @Query("""
        select count(pj) from PrintJob pj
        where pj.store_id = :storeId
          and pj.status = 'FAILED'
          and pj.created_at >= :startAt
          and pj.created_at < :endAt
        """)
    long countFailedByStoreIdAndCreatedAtBetween(
        @Param("storeId") Long storeId,
        @Param("startAt") LocalDateTime startAt,
        @Param("endAt") LocalDateTime endAt
    );

    @Query("""
        select max(pj.failed_at) from PrintJob pj
        where pj.store_id = :storeId
          and pj.status = 'FAILED'
        """)
    LocalDateTime findLastFailedAtByStoreId(@Param("storeId") Long storeId);

    @Query("""
        select pj from PrintJob pj
        where pj.store_id = :storeId
          and pj.executionMode = 'PAD_DIRECT'
          and (
            pj.status = 'PENDING'
            or (pj.status = 'CLAIMED' and pj.claimExpiresAt < :now)
          )
        order by pj.created_at asc, pj.id asc
        """)
    List<PrintJob> findPendingPadDirectJobs(
        @Param("storeId") Long storeId,
        @Param("now") LocalDateTime now,
        Pageable pageable
    );

    @Modifying
    @Query("""
        update PrintJob pj
        set pj.status = 'CLAIMED',
            pj.claimedByDeviceId = :deviceId,
            pj.claimedAt = :now,
            pj.claimExpiresAt = :claimExpiresAt,
            pj.clientAttemptToken = :clientAttemptToken,
            pj.last_attempt_at = :now,
            pj.updated_at = :now,
            pj.error_code = null,
            pj.error_message = null
        where pj.id = :jobId
          and pj.store_id = :storeId
          and pj.executionMode = 'PAD_DIRECT'
          and (
            pj.status = 'PENDING'
            or (pj.status = 'CLAIMED' and pj.claimExpiresAt < :now)
          )
        """)
    int claimPadDirectJob(
        @Param("jobId") Long jobId,
        @Param("storeId") Long storeId,
        @Param("deviceId") Long deviceId,
        @Param("clientAttemptToken") String clientAttemptToken,
        @Param("now") LocalDateTime now,
        @Param("claimExpiresAt") LocalDateTime claimExpiresAt
    );
}
