package com.restaurant.system.platform.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@Entity
@Table(name = "owner_store_onboarding_requests")
public class OwnerStoreOnboardingRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    public Long id;

    @Column(name = "organization_id", nullable = false)
    public Long organizationId;

    @Column(name = "idempotency_key", nullable = false)
    public String idempotencyKey;

    @Column(name = "request_fingerprint", nullable = false)
    public String requestFingerprint;

    @Column(name = "status", nullable = false)
    public String status;

    @Column(name = "store_id")
    public Long storeId;

    @Column(name = "result_code")
    public String resultCode;

    @Column(name = "error_code")
    public String errorCode;

    @Column(name = "created_at", nullable = false)
    public LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    public LocalDateTime updatedAt;

    @Column(name = "completed_at")
    public LocalDateTime completedAt;
}
