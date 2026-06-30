package com.restaurant.system.user.entity;

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
@Table(
    name = "store_memberships",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_store_membership_user_store", columnNames = {"user_id", "store_id"})
    }
)
public class StoreMembership {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    public Long id;

    @Column(name = "organization_id")
    public Long organizationId;

    @Column(name = "store_id")
    public Long storeId;

    @Column(name = "user_id")
    public Long userId;

    @Column(name = "role_id")
    public Long roleId;

    @Column(name = "role_code")
    public String roleCode;

    @Column(name = "is_active")
    public Boolean isActive;

    @Column(name = "created_at")
    public LocalDateTime createdAt;

    @Column(name = "updated_at")
    public LocalDateTime updatedAt;
}
