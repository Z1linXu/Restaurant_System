package com.restaurant.system.station.entity;

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
@Table(name = "dining_tables")
public class DiningTable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    public Long id;

    @Column(name = "store_id")
    public Long store_id;

    @Column(name = "table_code")
    public String table_code;

    @Column(name = "table_name")
    public String table_name;

    @Column(name = "area_name")
    public String area_name;

    @Column(name = "table_config")
    public String table_config;

    @Column(name = "capacity")
    public Integer capacity;

    @Column(name = "supports_split")
    public Boolean supports_split;

    @Column(name = "sort_order")
    public Integer sort_order;

    @Column(name = "is_active")
    public Boolean is_active;

    @Column(name = "created_at")
    public LocalDateTime created_at;

    @Column(name = "updated_at")
    public LocalDateTime updated_at;
}
