package com.restaurant.system.owner.provisioning.part2;

import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StorePart2RequestRepository extends JpaRepository<StoreProvisioningPart2RequestEntity, Long> {

    @Modifying
    @Query(value = """
        insert into store_provisioning_part2_requests (
            organization_id, store_id, idempotency_key, request_fingerprint,
            config_json, status, actor_user_id, created_at, updated_at
        ) values (
            :organizationId, :storeId, :idempotencyKey, :requestFingerprint,
            :configJson, 'PROCESSING', :actorUserId, :now, :now
        )
        on conflict (organization_id, store_id, idempotency_key) do nothing
        """, nativeQuery = true)
    int insertIfAbsent(
        @Param("organizationId") Long organizationId,
        @Param("storeId") Long storeId,
        @Param("idempotencyKey") String idempotencyKey,
        @Param("requestFingerprint") String requestFingerprint,
        @Param("configJson") String configJson,
        @Param("actorUserId") Long actorUserId,
        @Param("now") LocalDateTime now
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        select request from StoreProvisioningPart2RequestEntity request
        where request.organization_id = :organizationId
          and request.store_id = :storeId
          and request.idempotency_key = :idempotencyKey
        """)
    Optional<StoreProvisioningPart2RequestEntity> findForUpdate(
        @Param("organizationId") Long organizationId,
        @Param("storeId") Long storeId,
        @Param("idempotencyKey") String idempotencyKey
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select request from StoreProvisioningPart2RequestEntity request where request.id = :requestId")
    Optional<StoreProvisioningPart2RequestEntity> findByIdForUpdate(@Param("requestId") Long requestId);
}
