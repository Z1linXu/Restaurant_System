package com.restaurant.system.owner.provisioning.part2;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@Entity
@Table(
    name = "store_device_readiness",
    indexes = @Index(name = "idx_store_device_readiness_scope", columnList = "organization_id,store_id,proof_status,expires_at")
)
public class StoreDeviceReadinessEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "organization_id", nullable = false)
    public Long organization_id;

    @Column(name = "store_id", nullable = false)
    public Long store_id;

    @Column(name = "device_id", nullable = false)
    public Long device_id;

    @Column(name = "contract_version", nullable = false)
    public String contract_version;

    @Column(name = "trusted_build", nullable = false)
    public Boolean trusted_build;

    @Column(name = "worker_status", nullable = false)
    public String worker_status;

    @Column(name = "proof_status", nullable = false)
    public String proof_status;

    @Column(name = "last_heartbeat_at")
    public LocalDateTime last_heartbeat_at;

    @Column(name = "checked_at", nullable = false)
    public LocalDateTime checked_at;

    @Column(name = "expires_at", nullable = false)
    public LocalDateTime expires_at;

    @Column(name = "evidence_json", nullable = false, columnDefinition = "text")
    public String evidence_json;

    @Column(name = "created_at", nullable = false)
    public LocalDateTime created_at;

    @Column(name = "updated_at", nullable = false)
    public LocalDateTime updated_at;
}
