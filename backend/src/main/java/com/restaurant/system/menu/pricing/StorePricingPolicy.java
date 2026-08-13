package com.restaurant.system.menu.pricing;

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
@Table(name = "store_pricing_policies")
public class StorePricingPolicy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    public Long id;

    @Column(name = "store_id", nullable = false)
    public Long store_id;

    @Column(name = "size_small_delta", nullable = false)
    public BigDecimal size_small_delta;

    @Column(name = "size_regular_delta", nullable = false)
    public BigDecimal size_regular_delta;

    @Column(name = "size_large_delta", nullable = false)
    public BigDecimal size_large_delta;

    @Column(name = "combo_delta", nullable = false)
    public BigDecimal combo_delta;

    @Column(name = "policy_revision", nullable = false)
    public Long policy_revision;

    @Column(name = "created_at", nullable = false)
    public LocalDateTime created_at;

    @Column(name = "updated_at", nullable = false)
    public LocalDateTime updated_at;
}
