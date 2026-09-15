package com.restaurant.system.menu.addon;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@Entity
@Table(name = "organization_addon_definitions", uniqueConstraints =
    @UniqueConstraint(name = "uq_organization_addon_definitions_code", columnNames = {"organization_id", "code"}))
public class OrganizationAddonDefinition {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "organization_id", nullable = false, updatable = false)
    public Long organization_id;

    @Column(name = "code", nullable = false, updatable = false)
    public String code;

    @Column(name = "created_at", nullable = false)
    public LocalDateTime created_at;

    @Column(name = "updated_at", nullable = false)
    public LocalDateTime updated_at;
}
