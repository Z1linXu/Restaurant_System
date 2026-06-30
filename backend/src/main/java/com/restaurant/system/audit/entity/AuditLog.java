package com.restaurant.system.audit.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "audit_logs")
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "store_id")
    public Long store_id;

    @Column(name = "actor_user_id")
    public Long actor_user_id;

    @Column(name = "actor_name_snapshot")
    public String actor_name_snapshot;

    @Column(name = "actor_role_snapshot")
    public String actor_role_snapshot;

    @Column(name = "action", nullable = false)
    public String action;

    @Column(name = "entity_type")
    public String entity_type;

    @Column(name = "entity_id")
    public Long entity_id;

    @Column(name = "summary", length = 1000)
    public String summary;

    @Column(name = "metadata_json", columnDefinition = "TEXT")
    public String metadata_json;

    @Column(name = "created_at")
    public LocalDateTime created_at;

    @Column(name = "request_ip")
    public String request_ip;

    @Column(name = "user_agent", length = 1000)
    public String user_agent;
}
