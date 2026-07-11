package com.restaurant.system.printing.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@Entity
@Table(name = "store_devices")
public class StoreDevice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    public Long id;

    @Column(name = "organization_id")
    public Long organizationId;

    @Column(name = "store_id")
    public Long storeId;

    @Column(name = "device_name")
    public String deviceName;

    @Column(name = "device_type")
    public String deviceType;

    @Column(name = "device_token_hash")
    public String deviceTokenHash;

    @Column(name = "status")
    public String status;

    @Column(name = "last_seen_at")
    public LocalDateTime lastSeenAt;

    @Column(name = "app_version")
    public String appVersion;

    @Column(name = "platform")
    public String platform;

    @Column(name = "is_active")
    public Boolean isActive;

    @Column(name = "created_at")
    public LocalDateTime createdAt;

    @Column(name = "updated_at")
    public LocalDateTime updatedAt;
}
