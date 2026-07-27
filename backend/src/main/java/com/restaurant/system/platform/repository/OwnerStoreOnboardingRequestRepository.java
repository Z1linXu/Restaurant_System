package com.restaurant.system.platform.repository;

import com.restaurant.system.platform.entity.OwnerStoreOnboardingRequest;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OwnerStoreOnboardingRequestRepository extends JpaRepository<OwnerStoreOnboardingRequest, Long> {

    @Modifying
    @Query(value = """
        insert into owner_store_onboarding_requests (
            organization_id,
            idempotency_key,
            request_fingerprint,
            status,
            created_at,
            updated_at
        ) values (
            :organizationId,
            :idempotencyKey,
            :requestFingerprint,
            'PROCESSING',
            :now,
            :now
        )
        on conflict (organization_id, idempotency_key) do nothing
        """, nativeQuery = true)
    int insertIfAbsent(
        @Param("organizationId") Long organizationId,
        @Param("idempotencyKey") String idempotencyKey,
        @Param("requestFingerprint") String requestFingerprint,
        @Param("now") LocalDateTime now
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        select request from OwnerStoreOnboardingRequest request
        where request.organizationId = :organizationId
          and request.idempotencyKey = :idempotencyKey
        """)
    Optional<OwnerStoreOnboardingRequest> findForUpdate(
        @Param("organizationId") Long organizationId,
        @Param("idempotencyKey") String idempotencyKey
    );
}
