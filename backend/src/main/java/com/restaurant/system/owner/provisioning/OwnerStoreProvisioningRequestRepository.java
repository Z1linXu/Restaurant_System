package com.restaurant.system.owner.provisioning;

import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OwnerStoreProvisioningRequestRepository
    extends JpaRepository<OwnerStoreProvisioningRequestEntity, Long> {

    @Modifying
    @Query(value = """
        insert into owner_store_provisioning_requests (
            organization_id,
            idempotency_key,
            request_fingerprint,
            status,
            store_name,
            store_code,
            profile_code,
            profile_version,
            profile_fingerprint_sha256,
            master_menu_key,
            master_menu_version,
            master_menu_fingerprint_sha256,
            validation_status,
            actor_user_id,
            created_at,
            updated_at
        ) values (
            :organizationId,
            :idempotencyKey,
            :requestFingerprint,
            'PROCESSING',
            :storeName,
            :storeCode,
            :profileCode,
            :profileVersion,
            :profileFingerprintSha256,
            :masterMenuKey,
            :masterMenuVersion,
            :masterMenuFingerprintSha256,
            'PENDING',
            :actorUserId,
            :now,
            :now
        )
        on conflict (organization_id, idempotency_key) do nothing
        """, nativeQuery = true)
    int insertIfAbsent(
        @Param("organizationId") Long organizationId,
        @Param("idempotencyKey") String idempotencyKey,
        @Param("requestFingerprint") String requestFingerprint,
        @Param("storeName") String storeName,
        @Param("storeCode") String storeCode,
        @Param("profileCode") String profileCode,
        @Param("profileVersion") String profileVersion,
        @Param("profileFingerprintSha256") String profileFingerprintSha256,
        @Param("masterMenuKey") String masterMenuKey,
        @Param("masterMenuVersion") String masterMenuVersion,
        @Param("masterMenuFingerprintSha256") String masterMenuFingerprintSha256,
        @Param("actorUserId") Long actorUserId,
        @Param("now") LocalDateTime now
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        select request from OwnerStoreProvisioningRequestEntity request
        where request.organization_id = :organizationId
          and request.idempotency_key = :idempotencyKey
        """)
    Optional<OwnerStoreProvisioningRequestEntity> findForUpdate(
        @Param("organizationId") Long organizationId,
        @Param("idempotencyKey") String idempotencyKey
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select request from OwnerStoreProvisioningRequestEntity request where request.id = :requestId")
    Optional<OwnerStoreProvisioningRequestEntity> findByIdForUpdate(@Param("requestId") Long requestId);
}
