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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Data
@Entity
@Table(
    name = "owner_store_provisioning_requests",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_owner_store_provisioning_organization_key",
        columnNames = {"organization_id", "idempotency_key"}
    ),
    indexes = {
        @Index(name = "idx_owner_store_provisioning_store", columnList = "store_id"),
        @Index(
            name = "idx_owner_store_provisioning_status",
            columnList = "organization_id,status,created_at"
        )
    }
)
public class OwnerStoreProvisioningRequestEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    public Long id;

    @Column(name = "organization_id", nullable = false)
    public Long organization_id;

    @Column(name = "idempotency_key", nullable = false)
    public String idempotency_key;

    @Column(name = "request_fingerprint", nullable = false, columnDefinition = "char(64)", length = 64)
    @JdbcTypeCode(SqlTypes.CHAR)
    public String request_fingerprint;

    @Column(name = "status", nullable = false)
    public String status;

    @Column(name = "store_id")
    public Long store_id;

    @Column(name = "store_name", nullable = false)
    public String store_name;

    @Column(name = "store_code", nullable = false)
    public String store_code;

    @Column(name = "profile_code", nullable = false)
    public String profile_code;

    @Column(name = "profile_version", nullable = false)
    public String profile_version;

    @Column(name = "profile_fingerprint_sha256", nullable = false, columnDefinition = "char(64)", length = 64)
    @JdbcTypeCode(SqlTypes.CHAR)
    public String profile_fingerprint_sha256;

    @Column(name = "master_menu_key", nullable = false)
    public String master_menu_key;

    @Column(name = "master_menu_version", nullable = false)
    public String master_menu_version;

    @Column(name = "master_menu_fingerprint_sha256", nullable = false, columnDefinition = "char(64)", length = 64)
    @JdbcTypeCode(SqlTypes.CHAR)
    public String master_menu_fingerprint_sha256;

    @Column(name = "validation_status", nullable = false)
    public String validation_status;

    @Column(name = "result_code")
    public String result_code;

    @Column(name = "error_code")
    public String error_code;

    @Column(name = "created_station_count")
    public Integer created_station_count;

    @Column(name = "created_category_count")
    public Integer created_category_count;

    @Column(name = "created_item_count")
    public Integer created_item_count;

    @Column(name = "created_option_count")
    public Integer created_option_count;

    @Column(name = "created_pricing_policy_count")
    public Integer created_pricing_policy_count;

    @Column(name = "created_combo_component_count")
    public Integer created_combo_component_count;

    @Column(name = "created_printing_rule_count")
    public Integer created_printing_rule_count;

    @Column(name = "actor_user_id", nullable = false)
    public Long actor_user_id;

    @Column(name = "created_at", nullable = false)
    public LocalDateTime created_at;

    @Column(name = "updated_at", nullable = false)
    public LocalDateTime updated_at;

    @Column(name = "completed_at")
    public LocalDateTime completed_at;
}
