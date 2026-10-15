package com.restaurant.system.merchant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "store_merchant_configurations")
public class StoreMerchantConfiguration {
    @Id public Long store_id;
    @Column(nullable = false) public Integer schema_version;
    @Column(nullable = false) public Long config_revision;
    @Column(nullable = false, length = 255) public String brand_name;
    @Column(nullable = false, length = 64) public String locale;
    @Column(nullable = false, length = 3) public String currency;
    @Column(nullable = false, length = 96) public String tax_policy_reference;
    @Column(nullable = false, length = 96) public String profile_code;
    @Column(nullable = false, length = 64) public String profile_version;
    @Column(nullable = false, length = 32) public String compatibility_mode;
    @Column(nullable = false, length = 64) public String configuration_source;
    public Long updated_by;
    @Column(nullable = false) public LocalDateTime created_at;
    @Column(nullable = false) public LocalDateTime updated_at;
}
