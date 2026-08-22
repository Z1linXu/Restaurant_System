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
    name = "store_logical_printer_roles",
    indexes = @Index(name = "idx_store_logical_printer_scope", columnList = "organization_id,store_id,enabled")
)
public class StoreLogicalPrinterRoleEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "organization_id", nullable = false)
    public Long organization_id;

    @Column(name = "store_id", nullable = false)
    public Long store_id;

    @Column(name = "role_code", nullable = false)
    public String role_code;

    @Column(name = "module_code", nullable = false)
    public String module_code;

    @Column(name = "display_name", nullable = false)
    public String display_name;

    @Column(name = "mode", nullable = false)
    public String mode;

    @Column(name = "enabled", nullable = false)
    public Boolean enabled;

    @Column(name = "required", nullable = false)
    public Boolean required;

    @Column(name = "physical_binding_status", nullable = false)
    public String physical_binding_status;

    @Column(name = "assigned_printer_id")
    public Long assigned_printer_id;

    @Column(name = "created_at", nullable = false)
    public LocalDateTime created_at;

    @Column(name = "updated_at", nullable = false)
    public LocalDateTime updated_at;
}
