package com.restaurant.system.order.entity;

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
@Table(name = "order_submission_requests")
public class OrderSubmissionRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    public Long id;

    @Column(name = "organization_id", nullable = false)
    public Long organizationId;

    @Column(name = "store_id", nullable = false)
    public Long storeId;

    @Column(name = "idempotency_key", nullable = false)
    public String idempotencyKey;

    @Column(name = "client_order_id", nullable = false)
    public String clientOrderId;

    @Column(name = "payload_hash", nullable = false)
    public String payloadHash;

    @Column(name = "order_id")
    public Long orderId;

    @Column(name = "status", nullable = false)
    public String status;

    @Column(name = "error_code")
    public String errorCode;

    @Column(name = "created_at", nullable = false)
    public LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    public LocalDateTime updatedAt;

    @Column(name = "completed_at")
    public LocalDateTime completedAt;
}
