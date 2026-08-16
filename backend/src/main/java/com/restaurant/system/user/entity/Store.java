package com.restaurant.system.user.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Data;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Data
@Entity
@Table(name = "stores")
public class Store {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    public Long id;

    @Column(name = "organization_id")
    public Long organization_id;

    @Column(name = "name")
    public String name;

    @Column(name = "code")
    public String code;

    @Column(name = "status")
    public String status;

    @Column(name = "store_kind", nullable = false)
    public String store_kind = "BUSINESS";

    @Column(name = "lifecycle_status", nullable = false)
    public String lifecycle_status = "ACTIVE";

    @Column(name = "provisioning_source", nullable = false)
    public String provisioning_source = "LEGACY_EXISTING_STORE";

    @Column(name = "enable_bar_kitchen_tasks")
    public Boolean enable_bar_kitchen_tasks;

    @Column(name = "printing_enabled")
    public Boolean printing_enabled;

    @Column(name = "printing_mode")
    public String printing_mode;

    @Column(name = "provisioned_profile_code")
    public String provisioned_profile_code;

    @Column(name = "provisioned_profile_version")
    public String provisioned_profile_version;

    @Column(name = "provisioned_profile_fingerprint_sha256", columnDefinition = "char(64)", length = 64)
    @JdbcTypeCode(SqlTypes.CHAR)
    public String provisioned_profile_fingerprint_sha256;

    @Column(name = "provisioned_master_menu_key")
    public String provisioned_master_menu_key;

    @Column(name = "provisioned_master_menu_version")
    public String provisioned_master_menu_version;

    @Column(name = "provisioned_master_menu_fingerprint_sha256", columnDefinition = "char(64)", length = 64)
    @JdbcTypeCode(SqlTypes.CHAR)
    public String provisioned_master_menu_fingerprint_sha256;

    @Column(name = "menu_revision", nullable = false)
    public Long menu_revision = 1L;

    @Column(name = "menu_updated_at", nullable = false)
    public LocalDateTime menu_updated_at = LocalDateTime.now();

    @Column(name = "created_at")
    public LocalDateTime created_at;

    @Column(name = "updated_at")
    public LocalDateTime updated_at;
}
