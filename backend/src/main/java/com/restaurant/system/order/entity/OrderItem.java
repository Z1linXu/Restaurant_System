package com.restaurant.system.order.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@Entity
@Table(name = "order_items")
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    public Long id;

    @Column(name = "order_id")
    public Long order_id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
        name = "order_id",
        referencedColumnName = "id",
        insertable = false,
        updatable = false,
        foreignKey = @ForeignKey(name = "fk_order_items_order_id")
    )
    public Order order;

    @Column(name = "menu_item_id")
    public Long menu_item_id;

    @Column(name = "category_code_snapshot")
    public String category_code_snapshot;

    @Column(name = "item_name_snapshot_zh")
    public String item_name_snapshot_zh;

    @Column(name = "item_name_snapshot_en")
    public String item_name_snapshot_en;

    @Column(name = "quantity")
    public Integer quantity;

    @Column(name = "unit_price")
    public BigDecimal unit_price;

    @Column(name = "line_amount")
    public BigDecimal line_amount;

    @Column(name = "combo_group_no")
    public Integer combo_group_no;

    @Column(name = "combo_role")
    public String combo_role;

    @Column(name = "status")
    public String status;

    @Column(name = "notes")
    public String notes;

    @Column(name = "is_modified_after_submit")
    public Boolean is_modified_after_submit;

    @Column(name = "modified_after_submit_at")
    public LocalDateTime modified_after_submit_at;

    @Column(name = "created_at")
    public LocalDateTime created_at;

    @Column(name = "updated_at")
    public LocalDateTime updated_at;
}
