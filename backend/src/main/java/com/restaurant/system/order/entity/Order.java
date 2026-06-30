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
@Table(name = "orders")
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    public Long id;

    @Column(name = "store_id")
    public Long store_id;

    @Column(name = "created_by")
    public Long created_by;

    @Column(name = "order_no")
    public String order_no;

    @Column(name = "order_type")
    public String order_type;

    @Column(name = "status")
    public String status;

    @Column(name = "table_no")
    public String table_no;

    @Column(name = "pickup_no")
    public String pickup_no;

    @Column(name = "subtotal_amount")
    public BigDecimal subtotal_amount;

    @Column(name = "discount_amount")
    public BigDecimal discount_amount;

    @Column(name = "total_amount")
    public BigDecimal total_amount;

    @Column(name = "submitted_at")
    public LocalDateTime submitted_at;

    @Column(name = "ready_at")
    public LocalDateTime ready_at;

    @Column(name = "completed_at")
    public LocalDateTime completed_at;

    @Column(name = "is_modified_after_submit")
    public Boolean is_modified_after_submit;

    @Column(name = "modified_after_submit_at")
    public LocalDateTime modified_after_submit_at;

    @Column(name = "modified_after_submit_by")
    public Long modified_after_submit_by;

    @Column(name = "current_revision")
    public Integer current_revision;

    @Column(name = "created_at")
    public LocalDateTime created_at;

    @Column(name = "updated_at")
    public LocalDateTime updated_at;
}
