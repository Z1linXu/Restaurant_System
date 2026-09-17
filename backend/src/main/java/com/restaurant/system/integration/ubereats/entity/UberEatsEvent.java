package com.restaurant.system.integration.ubereats.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "uber_eats_events")
public class UberEatsEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "environment")
    public String environment;

    @Column(name = "event_id")
    public String eventId;

    @Column(name = "event_type")
    public String eventType;

    @Column(name = "uber_store_id")
    public String uberStoreId;

    @Column(name = "uber_order_id")
    public String uberOrderId;

    @Column(name = "body_hash")
    public String bodyHash;

    @Column(name = "status")
    public String status;

    @Column(name = "error_code")
    public String errorCode;

    @Column(name = "attempt_count")
    public Integer attemptCount;

    @Column(name = "next_attempt_at")
    public LocalDateTime nextAttemptAt;

    @Column(name = "created_at")
    public LocalDateTime createdAt;

    @Column(name = "updated_at")
    public LocalDateTime updatedAt;
}
