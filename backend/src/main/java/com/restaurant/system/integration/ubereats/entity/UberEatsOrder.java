package com.restaurant.system.integration.ubereats.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "uber_eats_orders")
public class UberEatsOrder {
    @Column(name = "processing_mode")
    public String processingMode = "ORDER_MANAGER";

    @Column(name = "accepted_observed_at")
    public LocalDateTime acceptedObservedAt;

    @Column(name = "current_state")
    public String currentState;

    @Column(name = "state_observed_at")
    public LocalDateTime stateObservedAt;

    @Column(name = "acceptance_poll_started_at")
    public LocalDateTime acceptancePollStartedAt;

    @Column(name = "acceptance_poll_expires_at")
    public LocalDateTime acceptancePollExpiresAt;

    @Column(name = "acceptance_poll_count")
    public Integer acceptancePollCount = 0;

    @Column(name = "released_at")
    public LocalDateTime releasedAt;

    @Column(name = "kitchen_dispatched_at")
    public LocalDateTime kitchenDispatchedAt;

    @Column(name = "customer_display_name")
    public String customerDisplayName;

    @Column(name = "raw_financial_snapshot_json", columnDefinition = "text")
    public String rawFinancialSnapshotJson;

    @Column(name = "item_financial_snapshot_json", columnDefinition = "text")
    public String itemFinancialSnapshotJson;

    @Column(name = "financial_currency")
    public String financialCurrency;

    @Column(name = "financial_total_minor")
    public Long financialTotalMinor;

    @Column(name = "financial_subtotal_minor")
    public Long financialSubtotalMinor;

    @Column(name = "financial_tax_minor")
    public Long financialTaxMinor;

    @Column(name = "financial_fees_minor")
    public Long financialFeesMinor;

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
