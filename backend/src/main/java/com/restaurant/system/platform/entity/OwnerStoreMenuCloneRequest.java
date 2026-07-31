package com.restaurant.system.platform.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;

@Entity
@Table(
    name = "owner_store_menu_clone_requests",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_owner_store_menu_clone_scope_key",
        columnNames = {"organization_id", "source_store_id", "target_store_id", "idempotency_key"}
    ),
    indexes = @Index(name = "idx_owner_store_menu_clone_target_store", columnList = "target_store_id")
)
public class OwnerStoreMenuCloneRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    public Long id;

    @Column(name = "organization_id", nullable = false)
    public Long organizationId;

    @Column(name = "source_store_id", nullable = false)
    public Long sourceStoreId;

    @Column(name = "target_store_id", nullable = false)
    public Long targetStoreId;

    @Column(name = "idempotency_key", nullable = false, length = 255)
    public String idempotencyKey;

    @Column(name = "request_fingerprint", nullable = false, length = 64)
    public String requestFingerprint;

    @Column(name = "profile_code", nullable = false, length = 96)
    public String profileCode;

    @Column(name = "status", nullable = false, length = 32)
    public String status;

    @Column(name = "source_menu_revision")
    public Long sourceMenuRevision;

    @Column(name = "target_revision_before")
    public Long targetRevisionBefore;

    @Column(name = "target_revision_after")
    public Long targetRevisionAfter;

    @Column(name = "created_station_count")
    public Integer createdStationCount;

    @Column(name = "created_category_count")
    public Integer createdCategoryCount;

    @Column(name = "created_item_count")
    public Integer createdItemCount;

    @Column(name = "created_option_count")
    public Integer createdOptionCount;

    @Column(name = "result_code", length = 64)
    public String resultCode;

    @Column(name = "error_code", length = 64)
    public String errorCode;

    @Column(name = "actor_user_id", nullable = false)
    public Long actorUserId;

    @Column(name = "created_at", nullable = false)
    public LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    public LocalDateTime updatedAt;

    @Column(name = "completed_at")
    public LocalDateTime completedAt;
}
