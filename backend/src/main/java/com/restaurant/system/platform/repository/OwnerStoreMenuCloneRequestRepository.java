package com.restaurant.system.platform.repository;

import com.restaurant.system.platform.entity.OwnerStoreMenuCloneRequest;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OwnerStoreMenuCloneRequestRepository extends JpaRepository<OwnerStoreMenuCloneRequest, Long> {

    @Modifying
    @Query(value = """
        insert into owner_store_menu_clone_requests (
            organization_id,
            source_store_id,
            target_store_id,
            idempotency_key,
            request_fingerprint,
            profile_code,
            status,
            actor_user_id,
            created_at,
            updated_at
        ) values (
            :organizationId,
            :sourceStoreId,
            :targetStoreId,
            :idempotencyKey,
            :requestFingerprint,
            :profileCode,
            'PROCESSING',
            :actorUserId,
            :now,
            :now
        )
        on conflict (organization_id, source_store_id, target_store_id, idempotency_key) do nothing
        """, nativeQuery = true)
    int insertIfAbsent(
        @Param("organizationId") Long organizationId,
        @Param("sourceStoreId") Long sourceStoreId,
        @Param("targetStoreId") Long targetStoreId,
        @Param("idempotencyKey") String idempotencyKey,
        @Param("requestFingerprint") String requestFingerprint,
        @Param("profileCode") String profileCode,
        @Param("actorUserId") Long actorUserId,
        @Param("now") LocalDateTime now
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        select request from OwnerStoreMenuCloneRequest request
        where request.organizationId = :organizationId
          and request.sourceStoreId = :sourceStoreId
          and request.targetStoreId = :targetStoreId
          and request.idempotencyKey = :idempotencyKey
        """)
    Optional<OwnerStoreMenuCloneRequest> findForUpdate(
        @Param("organizationId") Long organizationId,
        @Param("sourceStoreId") Long sourceStoreId,
        @Param("targetStoreId") Long targetStoreId,
        @Param("idempotencyKey") String idempotencyKey
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select request from OwnerStoreMenuCloneRequest request where request.id = :requestId")
    Optional<OwnerStoreMenuCloneRequest> findByIdForUpdate(@Param("requestId") Long requestId);
}
