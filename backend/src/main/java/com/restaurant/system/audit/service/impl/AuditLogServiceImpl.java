package com.restaurant.system.audit.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurant.system.audit.dto.AuditLogPageResponse;
import com.restaurant.system.audit.dto.AuditLogResponse;
import com.restaurant.system.audit.entity.AuditLog;
import com.restaurant.system.audit.repository.AuditLogRepository;
import com.restaurant.system.audit.service.AuditLogService;
import com.restaurant.system.common.auth.AuthenticatedUser;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class AuditLogServiceImpl implements AuditLogService {

    private static final Logger logger = LoggerFactory.getLogger(AuditLogServiceImpl.class);

    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;

    public AuditLogServiceImpl(AuditLogRepository auditLogRepository, ObjectMapper objectMapper) {
        this.auditLogRepository = auditLogRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    public void record(
        Long storeId,
        AuthenticatedUser actor,
        String action,
        String entityType,
        Long entityId,
        String summary,
        Map<String, ?> metadata,
        HttpServletRequest request
    ) {
        recordSystem(
            storeId,
            actor == null ? null : actor.userId(),
            actor == null ? null : displayName(actor),
            actor == null ? null : actor.roleCode(),
            action,
            entityType,
            entityId,
            summary,
            metadata,
            request
        );
    }

    @Override
    public void recordSystem(
        Long storeId,
        Long actorUserId,
        String actorName,
        String actorRole,
        String action,
        String entityType,
        Long entityId,
        String summary,
        Map<String, ?> metadata,
        HttpServletRequest request
    ) {
        try {
            AuditLog log = new AuditLog();
            log.store_id = storeId;
            log.actor_user_id = actorUserId;
            log.actor_name_snapshot = actorName;
            log.actor_role_snapshot = actorRole;
            log.action = action;
            log.entity_type = entityType;
            log.entity_id = entityId;
            log.summary = summary;
            log.metadata_json = metadata == null || metadata.isEmpty() ? null : objectMapper.writeValueAsString(metadata);
            log.created_at = LocalDateTime.now();
            log.request_ip = clientIp(request);
            log.user_agent = request == null ? null : request.getHeader("User-Agent");
            auditLogRepository.save(log);
        } catch (Exception exception) {
            logger.warn("Audit log write failed for action {}", action, exception);
        }
    }

    @Override
    public AuditLogPageResponse search(Long storeId, LocalDate date, String actor, String action, int page, int size) {
        LocalDate effectiveDate = date == null ? LocalDate.now() : date;
        LocalDateTime start = effectiveDate.atStartOfDay();
        LocalDateTime end = effectiveDate.plusDays(1).atStartOfDay();
        List<AuditLog> logs = storeId == null
            ? auditLogRepository.findAllByCreated_atBetweenOrderByCreated_atDesc(start, end)
            : auditLogRepository.findAllByStore_idAndCreated_atBetweenOrderByCreated_atDesc(storeId, start, end);

        String actorFilter = normalize(actor);
        String actionFilter = normalize(action);
        List<AuditLogResponse> filtered = logs.stream()
            .filter(log -> actorFilter == null || containsNormalized(log.actor_name_snapshot, actorFilter) || String.valueOf(log.actor_user_id).equals(actorFilter))
            .filter(log -> actionFilter == null || containsNormalized(log.action, actionFilter))
            .map(AuditLogResponse::from)
            .toList();

        int effectivePage = Math.max(page, 0);
        int effectiveSize = Math.max(1, Math.min(size, 200));
        int from = Math.min(effectivePage * effectiveSize, filtered.size());
        int to = Math.min(from + effectiveSize, filtered.size());

        AuditLogPageResponse response = new AuditLogPageResponse();
        response.records = filtered.subList(from, to);
        response.page = effectivePage;
        response.size = effectiveSize;
        response.totalCount = filtered.size();
        return response;
    }

    private String displayName(AuthenticatedUser actor) {
        if (actor.fullName() != null && !actor.fullName().isBlank()) {
            return actor.fullName();
        }
        return actor.username();
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim().toLowerCase();
    }

    private boolean containsNormalized(String value, String filter) {
        String normalized = normalize(value);
        return normalized != null && normalized.contains(filter);
    }

    private String clientIp(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
