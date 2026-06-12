package com.restaurant.system.auth.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "role_permissions")
public class RolePermission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "role_id", nullable = false)
    public Long roleId;

    @Column(name = "feature_package", nullable = false)
    public String featurePackage;

    @Column(name = "permission", nullable = false)
    public String permission;

    @Column(name = "capability_code")
    public String capabilityCode;

    @Column(name = "is_allowed")
    public Boolean isAllowed;

    @Column(name = "created_at")
    public LocalDateTime createdAt;

    @Column(name = "updated_at")
    public LocalDateTime updatedAt;
}
