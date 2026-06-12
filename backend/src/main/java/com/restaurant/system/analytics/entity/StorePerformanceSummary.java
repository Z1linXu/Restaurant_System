package com.restaurant.system.analytics.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@Entity
@Table(name = "store_performance_summary")
public class StorePerformanceSummary {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    public Long id;

    @Column(name = "summary_date")
    public LocalDate summary_date;

    @Column(name = "organization_id")
    public Long organization_id;

    @Column(name = "store_id")
    public Long store_id;

    @Column(name = "sales_amount")
    public BigDecimal sales_amount;

    @Column(name = "order_count")
    public Integer order_count;

    @Column(name = "average_order_value")
    public BigDecimal average_order_value;

    @Column(name = "active_order_count")
    public Integer active_order_count;

    @Column(name = "created_at")
    public LocalDateTime created_at;

    @Column(name = "updated_at")
    public LocalDateTime updated_at;
}
