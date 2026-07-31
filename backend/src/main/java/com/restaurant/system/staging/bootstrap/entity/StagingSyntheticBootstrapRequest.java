package com.restaurant.system.staging.bootstrap.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "staging_synthetic_bootstrap_requests")
public class StagingSyntheticBootstrapRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    public Long id;

    @Column(name = "run_id", nullable = false, unique = true)
    public String runId;

    @Column(name = "request_fingerprint", nullable = false)
    public String requestFingerprint;

    @Column(name = "status", nullable = false)
    public String status;

    @Column(name = "runtime_sha", nullable = false)
    public String runtimeSha;

    @Column(name = "tool_sha", nullable = false)
    public String toolSha;

    @Column(name = "organization_id")
    public Long organizationId;

    @Column(name = "source_store_id")
    public Long sourceStoreId;

    @Column(name = "owner_user_id")
    public Long ownerUserId;

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
