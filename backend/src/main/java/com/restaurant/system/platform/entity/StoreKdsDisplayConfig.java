package com.restaurant.system.platform.entity;

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
@Table(name = "store_kds_display_configs")
public class StoreKdsDisplayConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    public Long id;

    @Column(name = "store_id")
    public Long store_id;

    @Column(name = "screen_code")
    public String screen_code;

    @Column(name = "header_layout")
    public String header_layout;

    @Column(name = "density_mode")
    public String density_mode;

    @Column(name = "card_size_mode")
    public String card_size_mode;

    @Column(name = "config_json", columnDefinition = "TEXT")
    public String config_json;

    @Column(name = "created_at")
    public LocalDateTime created_at;

    @Column(name = "updated_at")
    public LocalDateTime updated_at;
}
