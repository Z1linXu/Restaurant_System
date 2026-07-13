package com.restaurant.system.printing.entity;

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
@Table(name = "order_dispatch_outbox")
public class OrderDispatchOutbox {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    public Long id;

    @Column(name = "organization_id", nullable = false)
    public Long organizationId;

    @Column(name = "store_id", nullable = false)
    public Long storeId;

    @Column(name = "order_id", nullable = false)
    public Long orderId;

    @Column(name = "order_update_batch_id")
    public Long orderUpdateBatchId;

    @Column(name = "module_code", nullable = false)
    public String moduleCode;

    @Column(name = "event_type", nullable = false)
    public String eventType;

    @Column(name = "source_key", nullable = false)
    public String sourceKey;

    @Column(name = "status", nullable = false)
    public String status;

    @Column(name = "attempt_count", nullable = false)
    public Integer attemptCount;

    @Column(name = "next_attempt_at", nullable = false)
    public LocalDateTime nextAttemptAt;

    @Column(name = "created_at", nullable = false)
    public LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    public LocalDateTime updatedAt;

    @Column(name = "completed_at")
    public LocalDateTime completedAt;

    @Column(name = "last_error", columnDefinition = "text")
    public String lastError;
}
