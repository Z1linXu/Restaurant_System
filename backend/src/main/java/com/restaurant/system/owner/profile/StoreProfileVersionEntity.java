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
@Table(name = "store_profile_versions")
public class StoreProfileVersionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    public Long id;

    @Column(name = "profile_id")
    public Long profile_id;

    @Column(name = "profile_version")
    public String profile_version;

    @Column(name = "status")
    public String status;

    @Column(name = "schema_version")
    public String schema_version;

    @Column(name = "content_json", columnDefinition = "text")
    public String content_json;

    @Column(name = "fingerprint_sha256", columnDefinition = "char(64)", length = 64)
    public String fingerprint_sha256;

    @Column(name = "source_reference")
    public String source_reference;

    @Column(name = "created_at")
    public LocalDateTime created_at;

    @Column(name = "updated_at")
    public LocalDateTime updated_at;

    @Column(name = "published_at")
    public LocalDateTime published_at;
}
