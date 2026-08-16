package com.restaurant.system.owner.master;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@Entity
@Table(
    name = "chain_master_menu_options",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uq_chain_master_menu_options_key",
            columnNames = {"master_menu_version_id", "master_option_key"}
        ),
        @UniqueConstraint(
            name = "uq_chain_master_menu_options_ref",
            columnNames = {"master_menu_version_id", "option_ref"}
        )
    },
    indexes = {
        @Index(
            name = "idx_chain_master_menu_options_version",
            columnList = "master_menu_version_id,sort_order"
        ),
        @Index(
            name = "idx_chain_master_menu_options_product",
            columnList = "master_menu_version_id,master_product_key"
        )
    }
)
public class ChainMasterMenuOptionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    public Long id;

    @Column(name = "master_menu_version_id", nullable = false)
    public Long master_menu_version_id;

    @Column(name = "master_option_key", nullable = false)
    public String master_option_key;

    @Column(name = "option_ref", nullable = false)
    public String option_ref;

    @Column(name = "master_product_key", nullable = false)
    public String master_product_key;

    @Column(name = "parent_master_option_key")
    public String parent_master_option_key;

    @Column(name = "option_type")
    public String option_type;

    @Column(name = "option_group")
    public String option_group;

    @Column(name = "code")
    public String code;

    @Column(name = "name", nullable = false)
    public String name;

    @Column(name = "name_en")
    public String name_en;

    @Column(name = "name_zh")
    public String name_zh;

    @Column(name = "price_delta", nullable = false, precision = 10, scale = 2)
    public BigDecimal price_delta = BigDecimal.ZERO;

    @Column(name = "sort_order", nullable = false)
    public Integer sort_order = 0;

    @Column(name = "default_active", nullable = false)
    public Boolean default_active = true;

    @Column(name = "created_at", nullable = false)
    public LocalDateTime created_at;

    @Column(name = "updated_at", nullable = false)
    public LocalDateTime updated_at;
}
