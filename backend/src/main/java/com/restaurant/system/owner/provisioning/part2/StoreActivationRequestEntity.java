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
    name = "store_activation_requests",
    indexes = @Index(name = "idx_store_activation_request_store", columnList = "organization_id,store_id,created_at")
)
public class StoreActivationRequestEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "organization_id", nullable = false)
    public Long organization_id;

    @Column(name = "store_id", nullable = false)
    public Long store_id;

    @Column(name = "idempotency_key", nullable = false)
    public String idempotency_key;

    @Column(name = "request_fingerprint", nullable = false, columnDefinition = "char(64)", length = 64)
    @JdbcTypeCode(SqlTypes.CHAR)
    public String request_fingerprint;

    @Column(name = "expected_readiness_fingerprint", columnDefinition = "char(64)", length = 64)
    @JdbcTypeCode(SqlTypes.CHAR)
    public String expected_readiness_fingerprint;

    @Column(name = "status", nullable = false)
    public String status;

    @Column(name = "target_state")
    public String target_state;

    @Column(name = "readiness_evidence_id")
    public Long readiness_evidence_id;

    @Column(name = "result_code")
    public String result_code;

    @Column(name = "error_code")
    public String error_code;

    @Column(name = "result_json", columnDefinition = "text")
    public String result_json;

    @Column(name = "actor_user_id", nullable = false)
    public Long actor_user_id;

    @Column(name = "created_at", nullable = false)
    public LocalDateTime created_at;

    @Column(name = "updated_at", nullable = false)
    public LocalDateTime updated_at;

    @Column(name = "completed_at")
    public LocalDateTime completed_at;
}
