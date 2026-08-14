package com.restaurant.system.owner.profile;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Data;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Data
@Entity
@Table(name = "store_profile_artifacts")
public class StoreProfileArtifactEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    public Long id;

    @Column(name = "profile_version_id")
    public Long profile_version_id;

    @Column(name = "artifact_type")
    public String artifact_type;

    @Column(name = "artifact_code")
    public String artifact_code;

    @Column(name = "artifact_version")
    public String artifact_version;

    @Column(name = "content_json", columnDefinition = "text")
    public String content_json;

    @Column(name = "fingerprint_sha256", columnDefinition = "char(64)", length = 64)
    @JdbcTypeCode(SqlTypes.CHAR)
    public String fingerprint_sha256;

    @Column(name = "created_at")
    public LocalDateTime created_at;

    @Column(name = "updated_at")
    public LocalDateTime updated_at;
}
