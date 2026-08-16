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
    name = "chain_master_menus",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_chain_master_menus_org_key",
        columnNames = {"organization_id", "master_menu_key"}
    ),
    indexes = @Index(
        name = "idx_chain_master_menus_organization_status",
        columnList = "organization_id,status"
    )
)
public class ChainMasterMenuEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    public Long id;

    @Column(name = "organization_id", nullable = false)
    public Long organization_id;

    @Column(name = "master_menu_key", nullable = false)
    public String master_menu_key;

    @Column(name = "display_name", nullable = false)
    public String display_name;

    @Column(name = "description", columnDefinition = "text")
    public String description;

    @Column(name = "status", nullable = false)
    public String status;

    @Column(name = "provenance", nullable = false)
    public String provenance;

    @Column(name = "created_at", nullable = false)
    public LocalDateTime created_at;

    @Column(name = "updated_at", nullable = false)
    public LocalDateTime updated_at;
}
