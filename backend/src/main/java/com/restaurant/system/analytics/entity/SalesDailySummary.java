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
@Table(name = "sales_daily_summary")
public class SalesDailySummary {

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

    @Column(name = "gross_sales")
    public BigDecimal gross_sales;

    @Column(name = "net_sales")
    public BigDecimal net_sales;

    @Column(name = "order_count")
    public Integer order_count;

    @Column(name = "completed_order_count")
    public Integer completed_order_count;

    @Column(name = "cancelled_order_count")
    public Integer cancelled_order_count;

    @Column(name = "average_order_value")
    public BigDecimal average_order_value;

    @Column(name = "total_cost")
    public BigDecimal total_cost;

    @Column(name = "total_profit")
    public BigDecimal total_profit;

    @Column(name = "profit_margin")
    public BigDecimal profit_margin;

    @Column(name = "created_at")
    public LocalDateTime created_at;

    @Column(name = "updated_at")
    public LocalDateTime updated_at;
}
