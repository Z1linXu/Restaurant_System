package com.restaurant.system.owner.provisioning.part2;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Data;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Data
@Entity
@Table(
    name = "store_readiness_evidence_history",
    indexes = @Index(
        name = "idx_store_readiness_evidence_history_scope",
        columnList = "organization_id,store_id,created_at"
    )
)
public class StoreReadinessEvidenceHistoryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "organization_id", nullable = false)
    public Long organization_id;

    @Column(name = "store_id", nullable = false)
    public Long store_id;

    @Column(name = "status", nullable = false)
    public String status;

    @Column(name = "readiness_fingerprint", nullable = false, columnDefinition = "char(64)", length = 64)
    @JdbcTypeCode(SqlTypes.CHAR)
    public String readiness_fingerprint;

    @Column(name = "evidence_json", nullable = false, columnDefinition = "text")
    public String evidence_json;

    @Column(name = "checked_at", nullable = false)
    public LocalDateTime checked_at;

    @Column(name = "expires_at", nullable = false)
    public LocalDateTime expires_at;

    @Column(name = "created_at", nullable = false)
    public LocalDateTime created_at;
}
