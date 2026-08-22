package com.restaurant.system.owner.provisioning.part2;

import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StoreActivationRequestRepository extends JpaRepository<StoreActivationRequestEntity, Long> {

    @Modifying
    @Query(value = """
        insert into store_activation_requests (
            organization_id, store_id, idempotency_key, request_fingerprint,
            expected_readiness_fingerprint, status, actor_user_id, created_at, updated_at
        ) values (
            :organizationId, :storeId, :idempotencyKey, :requestFingerprint,
            :expectedReadinessFingerprint, 'PROCESSING', :actorUserId, :now, :now
        )
        on conflict (organization_id, store_id, idempotency_key) do nothing
        """, nativeQuery = true)
    int insertIfAbsent(
        @Param("organizationId") Long organizationId,
        @Param("storeId") Long storeId,
        @Param("idempotencyKey") String idempotencyKey,
        @Param("requestFingerprint") String requestFingerprint,
        @Param("expectedReadinessFingerprint") String expectedReadinessFingerprint,
        @Param("actorUserId") Long actorUserId,
        @Param("now") LocalDateTime now
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        select request from StoreActivationRequestEntity request
        where request.organization_id = :organizationId
          and request.store_id = :storeId
          and request.idempotency_key = :idempotencyKey
        """)
    Optional<StoreActivationRequestEntity> findForUpdate(
        @Param("organizationId") Long organizationId,
        @Param("storeId") Long storeId,
        @Param("idempotencyKey") String idempotencyKey
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select request from StoreActivationRequestEntity request where request.id = :requestId")
    Optional<StoreActivationRequestEntity> findByIdForUpdate(@Param("requestId") Long requestId);
}
