package com.restaurant.system.integration.ubereats.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "uber_eats_store_mappings")
public class UberEatsStoreMapping {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "environment")
    public String environment;

    @Column(name = "uber_store_id")
    public String uberStoreId;

    @Column(name = "store_id")
    public Long storeId;

    @Column(name = "organization_id")
    public Long organizationId;

    @Column(name = "enabled")
    public Boolean enabled;

    @Column(name = "created_at")
    public LocalDateTime createdAt;
}
