package com.restaurant.system.modules;

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
@Table(name = "store_modules")
public class StoreModule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    public Long id;

    @Column(name = "store_id")
    public Long store_id;

    @Column(name = "module_key")
    public String module_key;

    @Column(name = "enabled")
    public Boolean enabled;

    @Column(name = "source")
    public String source;

    @Column(name = "configuration_status")
    public String configuration_status;

    @Column(name = "profile_code")
    public String profile_code;

    @Column(name = "profile_version")
    public String profile_version;

    @Column(name = "metadata_json", columnDefinition = "text")
    public String metadata_json;

    @Column(name = "created_at")
    public LocalDateTime created_at;

    @Column(name = "updated_at")
    public LocalDateTime updated_at;
}
