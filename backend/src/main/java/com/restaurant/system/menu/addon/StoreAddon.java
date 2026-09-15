package com.restaurant.system.menu.addon;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@Entity
@Table(name = "store_addons", uniqueConstraints =
    @UniqueConstraint(name = "uq_store_addons_definition", columnNames = {"store_id", "organization_addon_definition_id"}))
public class StoreAddon {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "store_id", nullable = false, updatable = false)
    public Long store_id;

    // Scope anchor shared by the Store and Organization definition foreign keys.
    @Column(name = "organization_id", nullable = false, updatable = false)
    public Long organization_id;

    @Column(name = "organization_addon_definition_id", nullable = false, updatable = false)
    public Long organization_addon_definition_id;

    @Column(name = "name_zh", nullable = false)
    public String name_zh;

    @Column(name = "name_en")
    public String name_en;

    @Column(name = "price", nullable = false, precision = 38, scale = 2)
    public BigDecimal price;

    @Column(name = "active", nullable = false)
    public Boolean active;

    @Column(name = "created_at", nullable = false)
    public LocalDateTime created_at;

    @Column(name = "updated_at", nullable = false)
    public LocalDateTime updated_at;
}
