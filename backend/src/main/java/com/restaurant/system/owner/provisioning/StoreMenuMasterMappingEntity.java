package com.restaurant.system.owner.provisioning;

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
    name = "store_menu_master_mappings",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_store_menu_master_mappings_local",
        columnNames = {"store_id", "entity_type", "local_entity_id"}
    ),
    indexes = {
        @Index(
            name = "idx_store_menu_master_mappings_store_version",
            columnList = "store_id,master_menu_version_id"
        ),
        @Index(
            name = "idx_store_menu_master_mappings_master_identity",
            columnList = "master_menu_version_id,master_category_key,master_product_key,master_option_key"
        )
    }
)
public class StoreMenuMasterMappingEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    public Long id;

    @Column(name = "store_id", nullable = false)
    public Long store_id;

    @Column(name = "master_menu_version_id", nullable = false)
    public Long master_menu_version_id;

    @Column(name = "entity_type", nullable = false)
    public String entity_type;

    @Column(name = "local_entity_id", nullable = false)
    public Long local_entity_id;

    @Column(name = "master_category_key")
    public String master_category_key;

    @Column(name = "master_product_key")
    public String master_product_key;

    @Column(name = "master_option_key")
    public String master_option_key;

    @Column(name = "origin", nullable = false)
    public String origin;

    @Column(name = "mapping_status", nullable = false)
    public String mapping_status;

    @Column(name = "created_at", nullable = false)
    public LocalDateTime created_at;

    @Column(name = "updated_at", nullable = false)
    public LocalDateTime updated_at;
}
