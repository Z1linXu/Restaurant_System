package com.restaurant.system.order.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@Entity
@Table(name = "order_item_options")
public class OrderItemOption {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", columnDefinition = "BIGSERIAL")
    public Long id;

    @Column(name = "order_item_id")
    public Long order_item_id;

    @Column(name = "option_id")
    public Long option_id;

    @Column(name = "option_type_snapshot")
    public String option_type_snapshot;

    @Column(name = "option_name_snapshot_zh")
    public String option_name_snapshot_zh;

    @Column(name = "option_name_snapshot_en")
    public String option_name_snapshot_en;

    @Column(name = "price_delta")
    public BigDecimal price_delta;

    @Column(name = "quantity")
    public Integer quantity;

    @Column(name = "created_at")
    public LocalDateTime created_at;
}
