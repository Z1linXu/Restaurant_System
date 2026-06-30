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
    name = "organization_memberships",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_org_membership_user_org", columnNames = {"user_id", "organization_id"})
    }
)
public class OrganizationMembership {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    public Long id;

    @Column(name = "organization_id")
    public Long organizationId;

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
