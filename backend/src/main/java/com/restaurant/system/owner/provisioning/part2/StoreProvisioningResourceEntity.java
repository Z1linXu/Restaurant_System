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
    name = "store_provisioning_resources",
    indexes = @Index(name = "idx_store_part2_resource_request", columnList = "request_id,resource_type")
)
public class StoreProvisioningResourceEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "organization_id", nullable = false)
    public Long organization_id;

    @Column(name = "store_id", nullable = false)
    public Long store_id;

    @Column(name = "resource_type", nullable = false)
    public String resource_type;

    @Column(name = "resource_code", nullable = false)
    public String resource_code;

    @Column(name = "target_id", nullable = false)
    public Long target_id;

    @Column(name = "request_id", nullable = false)
    public Long request_id;

    @Column(name = "created_at", nullable = false)
    public LocalDateTime created_at;
}
