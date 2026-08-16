package com.restaurant.system.owner.master;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.Data;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Data
@Entity
@Table(
    name = "chain_master_menu_versions",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uq_chain_master_menu_versions_key",
            columnNames = {"master_menu_id", "version_key"}
        ),
        @UniqueConstraint(
            name = "uq_chain_master_menu_versions_fingerprint",
            columnNames = {"master_menu_id", "fingerprint_sha256"}
        )
    },
    indexes = @Index(
        name = "idx_chain_master_menu_versions_menu_status",
        columnList = "master_menu_id,status,version_key"
    )
)
public class ChainMasterMenuVersionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    public Long id;

    @Column(name = "master_menu_id", nullable = false)
    public Long master_menu_id;

    @Column(name = "version_key", nullable = false)
    public String version_key;

    @Column(name = "status", nullable = false)
    public String status;

    @Column(name = "schema_version", nullable = false)
    public String schema_version;

    @Column(name = "content_json", nullable = false, columnDefinition = "text")
    public String content_json;

    @Column(name = "fingerprint_sha256", nullable = false, columnDefinition = "char(64)", length = 64)
    @JdbcTypeCode(SqlTypes.CHAR)
    public String fingerprint_sha256;

    @Column(name = "source_profile_code", nullable = false)
    public String source_profile_code;

    @Column(name = "source_profile_version", nullable = false)
    public String source_profile_version;

    @Column(name = "source_profile_fingerprint_sha256", nullable = false, columnDefinition = "char(64)", length = 64)
    @JdbcTypeCode(SqlTypes.CHAR)
    public String source_profile_fingerprint_sha256;

    @Column(name = "source_reference", nullable = false)
    public String source_reference;

    @Column(name = "created_at", nullable = false)
    public LocalDateTime created_at;

    @Column(name = "updated_at", nullable = false)
    public LocalDateTime updated_at;

    @Column(name = "published_at")
    public LocalDateTime published_at;
}
