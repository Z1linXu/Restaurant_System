package com.restaurant.system.integration.ubereats.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "uber_eats_menu_mappings")
public class UberEatsMenuMapping {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "store_mapping_id")
    public Long storeMappingId;

    @Column(name = "kind")
    public String kind;

    @Column(name = "identifier_type")
    public String identifierType;

    @Column(name = "uber_identifier")
    public String uberIdentifier;

    @Column(name = "uber_item_id")
    public String uberItemId;

    @Column(name = "local_menu_item_id")
    public Long localMenuItemId;

    @Column(name = "local_option_code")
    public String localOptionCode;

    @Column(name = "local_option_group")
    public String localOptionGroup;

    @Column(name = "parent_option_code")
    public String parentOptionCode;

    @Column(name = "updated_at")
    public LocalDateTime updatedAt;
}
