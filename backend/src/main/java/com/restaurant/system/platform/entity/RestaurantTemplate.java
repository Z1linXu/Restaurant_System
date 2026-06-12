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
@Table(name = "restaurant_templates")
public class RestaurantTemplate {

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

    @Column(name = "description")
    public String description;

    @Column(name = "source_store_id")
    public Long source_store_id;

    @Column(name = "default_station_setup_json", columnDefinition = "TEXT")
    public String default_station_setup_json;

    @Column(name = "default_kds_display_rules_json", columnDefinition = "TEXT")
    public String default_kds_display_rules_json;

    @Column(name = "default_menu_category_structure_json", columnDefinition = "TEXT")
    public String default_menu_category_structure_json;

    @Column(name = "default_dining_table_layout_rules_json", columnDefinition = "TEXT")
    public String default_dining_table_layout_rules_json;

    @Column(name = "default_role_setup_json", columnDefinition = "TEXT")
    public String default_role_setup_json;

    @Column(name = "is_active")
    public Boolean is_active;

    @Column(name = "created_at")
    public LocalDateTime created_at;

    @Column(name = "updated_at")
    public LocalDateTime updated_at;
}
