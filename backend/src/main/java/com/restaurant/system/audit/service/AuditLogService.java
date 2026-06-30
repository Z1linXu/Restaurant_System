package com.restaurant.system.audit.service;

import com.restaurant.system.audit.dto.AuditLogPageResponse;
import com.restaurant.system.common.auth.AuthenticatedUser;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDate;
import java.util.Map;

public interface AuditLogService {

    void record(
        Long storeId,
        AuthenticatedUser actor,
        String action,
        String entityType,
        Long entityId,
        String summary,
        Map<String, ?> metadata,
        HttpServletRequest request
    );

    void recordSystem(
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
    );

    AuditLogPageResponse search(Long storeId, LocalDate date, String actor, String action, int page, int size);
}
