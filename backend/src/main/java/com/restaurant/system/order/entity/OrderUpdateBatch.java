package com.restaurant.system.order.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@Entity
@Table(
    name = "order_update_batches",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_order_update_batch_revision", columnNames = {"order_id", "revision"}),
        @UniqueConstraint(name = "uk_order_update_batch_idempotency", columnNames = {"order_id", "idempotency_key"})
    }
)
public class OrderUpdateBatch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    public Long id;

    @Column(name = "order_id", nullable = false)
    public Long order_id;

    @Column(name = "revision", nullable = false)
    public Integer revision;

    @Column(name = "idempotency_key", nullable = false)
    public String idempotency_key;

    @Column(name = "created_by")
    public Long created_by;

    @Column(name = "created_at")
    public LocalDateTime created_at;
}
