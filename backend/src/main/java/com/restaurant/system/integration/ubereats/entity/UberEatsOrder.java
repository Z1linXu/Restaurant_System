package com.restaurant.system.integration.ubereats.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "uber_eats_orders")
public class UberEatsOrder {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "environment")
    public String environment;

    @Column(name = "store_mapping_id")
    public Long storeMappingId;

    @Column(name = "store_id")
    public Long storeId;

    @Column(name = "uber_store_id")
    public String uberStoreId;

    @Column(name = "uber_order_id")
    public String uberOrderId;

    @Column(name = "uber_event_id")
    public String uberEventId;

    @Column(name = "status")
    public String status;

    @Column(name = "display_id")
    public String displayId;

    @Column(name = "fulfillment_type")
    public String fulfillmentType;

    @Column(name = "mapping_status")
    public String mappingStatus;

    @Column(name = "mapping_error", columnDefinition = "text")
    public String mappingError;

    @Column(name = "raw_order_snapshot_json", columnDefinition = "text")
    public String rawOrderSnapshotJson;

    @Column(name = "local_request_json", columnDefinition = "text")
    public String localRequestJson;

    @Column(name = "last_error")
    public String lastError;

    @Column(name = "deny_reason")
    public String denyReason;

    @Column(name = "accepted_by")
    public Long acceptedBy;

    @Column(name = "local_order_id")
    public Long localOrderId;

    @Column(name = "attempt_count")
    public Integer attemptCount;

    @Column(name = "cancelled")
    public Boolean cancelled;

    @Column(name = "edit_required")
    public Boolean editRequired;

    @Column(name = "scheduled")
    public Boolean scheduled;

    @Column(name = "placed_at")
    public LocalDateTime placedAt;

    @Column(name = "scheduled_at")
    public LocalDateTime scheduledAt;

    @Column(name = "accepted_at")
    public LocalDateTime acceptedAt;

    @Column(name = "cancelled_at")
    public LocalDateTime cancelledAt;

    @Column(name = "next_attempt_at")
    public LocalDateTime nextAttemptAt;

    @Column(name = "created_at")
    public LocalDateTime createdAt;

    @Column(name = "updated_at")
    public LocalDateTime updatedAt;
}
