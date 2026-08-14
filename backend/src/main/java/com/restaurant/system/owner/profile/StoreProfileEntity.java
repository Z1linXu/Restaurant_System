package com.restaurant.system.owner.profile;

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
@Table(name = "store_profiles")
public class StoreProfileEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    public Long id;

    @Column(name = "profile_code")
    public String profile_code;

    @Column(name = "display_name")
    public String display_name;

    @Column(name = "description", columnDefinition = "text")
    public String description;

    @Column(name = "status")
    public String status;

    @Column(name = "provenance")
    public String provenance;

    @Column(name = "created_at")
    public LocalDateTime created_at;

    @Column(name = "updated_at")
    public LocalDateTime updated_at;
}
