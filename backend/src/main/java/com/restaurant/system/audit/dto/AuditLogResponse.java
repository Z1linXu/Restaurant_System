package com.restaurant.system.audit.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.restaurant.system.audit.entity.AuditLog;
import java.time.LocalDateTime;

public class AuditLogResponse {

    public Long id;

    @JsonProperty("store_id")
    public Long storeId;

    @JsonProperty("actor_user_id")
    public Long actorUserId;

    @JsonProperty("actor_name_snapshot")
    public String actorNameSnapshot;

    @JsonProperty("actor_role_snapshot")
    public String actorRoleSnapshot;

    public String action;

    @JsonProperty("entity_type")
    public String entityType;

    @JsonProperty("entity_id")
    public Long entityId;

    public String summary;

    @JsonProperty("metadata_json")
    public String metadataJson;

    @JsonProperty("created_at")
    public LocalDateTime createdAt;

    @JsonProperty("request_ip")
    public String requestIp;

    @JsonProperty("user_agent")
    public String userAgent;

    public static AuditLogResponse from(AuditLog log) {
        AuditLogResponse response = new AuditLogResponse();
        response.id = log.id;
        response.storeId = log.store_id;
        response.actorUserId = log.actor_user_id;
        response.actorNameSnapshot = log.actor_name_snapshot;
        response.actorRoleSnapshot = log.actor_role_snapshot;
        response.action = log.action;
        response.entityType = log.entity_type;
        response.entityId = log.entity_id;
        response.summary = log.summary;
        response.metadataJson = log.metadata_json;
        response.createdAt = log.created_at;
        response.requestIp = log.request_ip;
        response.userAgent = log.user_agent;
        return response;
    }
}
