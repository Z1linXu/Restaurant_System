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
@Table(name = "menu_item_sales_summary")
public class MenuItemSalesSummary {

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

    @Column(name = "menu_item_id")
    public Long menu_item_id;

    @Column(name = "item_name_snapshot_zh")
    public String item_name_snapshot_zh;

    @Column(name = "item_name_snapshot_en")
    public String item_name_snapshot_en;

    @Column(name = "quantity_sold")
    public Integer quantity_sold;

    @Column(name = "sales_amount")
    public BigDecimal sales_amount;

    @Column(name = "total_cost")
    public BigDecimal total_cost;

    @Column(name = "total_profit")
    public BigDecimal total_profit;

    @Column(name = "order_count")
    public Integer order_count;

    @Column(name = "created_at")
    public LocalDateTime created_at;

    @Column(name = "updated_at")
    public LocalDateTime updated_at;
}
