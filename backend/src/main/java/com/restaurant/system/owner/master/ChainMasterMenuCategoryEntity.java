package com.restaurant.system.owner.master;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@Entity
@Table(
    name = "chain_master_menu_categories",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uq_chain_master_menu_categories_key",
            columnNames = {"master_menu_version_id", "master_category_key"}
        ),
        @UniqueConstraint(
            name = "uq_chain_master_menu_categories_ref",
            columnNames = {"master_menu_version_id", "category_ref"}
        )
    },
    indexes = @Index(
        name = "idx_chain_master_menu_categories_version",
        columnList = "master_menu_version_id,sort_order"
    )
)
public class ChainMasterMenuCategoryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    public Long id;

    @Column(name = "master_menu_version_id", nullable = false)
    public Long master_menu_version_id;

    @Column(name = "master_category_key", nullable = false)
    public String master_category_key;

    @Column(name = "category_ref", nullable = false)
    public String category_ref;

    @Column(name = "code")
    public String code;

    @Column(name = "name", nullable = false)
    public String name;

    @Column(name = "name_en")
    public String name_en;

    @Column(name = "name_zh")
    public String name_zh;

    @Column(name = "sort_order", nullable = false)
    public Integer sort_order = 0;

    @Column(name = "default_active", nullable = false)
    public Boolean default_active = true;

    @Column(name = "created_at", nullable = false)
    public LocalDateTime created_at;

    @Column(name = "updated_at", nullable = false)
    public LocalDateTime updated_at;
}
