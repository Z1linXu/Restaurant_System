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

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select pj from PrintJob pj where pj.id = :id")
    java.util.Optional<PrintJob> findLockedById(@Param("id") Long id);

    java.util.Optional<PrintJob> findByDispatchSourceKey(String dispatchSourceKey);

    @Query("""
        select pj from PrintJob pj where pj.store_id = :storeId
          and ((:orderId is null and pj.order_id is null) or pj.order_id = :orderId)
          and pj.module_code = :module and pj.status in ('PENDING', 'CLAIMED', 'PRINTING') order by pj.id
        """)
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    List<PrintJob> findActiveForReprint(@Param("storeId") Long storeId, @Param("orderId") Long orderId, @Param("module") String module);

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
            (pj.status = 'PENDING' and (pj.preferredDeviceId is null or pj.preferredDeviceId = :deviceId
                or pj.preferredDeviceUntil is null or pj.preferredDeviceUntil <= :now))
            or (pj.status = 'CLAIMED' and pj.claimExpiresAt < :now)
          )
        order by pj.created_at asc, pj.id asc
        """)
    List<PrintJob> findPendingPadDirectJobs(
        @Param("storeId") Long storeId,
        @Param("deviceId") Long deviceId,
        @Param("now") LocalDateTime now,
        Pageable pageable
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
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
            (pj.status = 'PENDING' and (pj.preferredDeviceId is null or pj.preferredDeviceId = :deviceId
                or pj.preferredDeviceUntil is null or pj.preferredDeviceUntil <= :now))
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

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        update PrintJob pj set pj.status = 'PRINTING', pj.claimExpiresAt = :expires,
            pj.printingStartedAt = coalesce(pj.printingStartedAt, :now), pj.last_attempt_at = :now, pj.updated_at = :now
        where pj.id = :id and pj.store_id = :storeId and pj.executionMode = 'PAD_DIRECT'
          and pj.claimedByDeviceId = :deviceId and pj.clientAttemptToken = :token
          and ((pj.status = 'CLAIMED' and pj.claimExpiresAt > :now) or pj.status = 'PRINTING')
        """)
    int startPadPrint(@Param("id") Long id, @Param("storeId") Long storeId, @Param("deviceId") Long deviceId,
        @Param("token") String token, @Param("now") LocalDateTime now, @Param("expires") LocalDateTime expires);
}
