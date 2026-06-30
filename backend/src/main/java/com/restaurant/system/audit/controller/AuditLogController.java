package com.restaurant.system.audit.controller;

import com.restaurant.system.audit.dto.AuditLogPageResponse;
import com.restaurant.system.audit.service.AuditLogService;
import com.restaurant.system.common.auth.AuthorizationService;
import com.restaurant.system.common.auth.Capability;
import com.restaurant.system.common.response.ApiResponse;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/audit-logs")
public class AuditLogController {

    private final AuditLogService auditLogService;
    private final AuthorizationService authorizationService;

    public AuditLogController(AuditLogService auditLogService, AuthorizationService authorizationService) {
        this.auditLogService = auditLogService;
        this.authorizationService = authorizationService;
    }

    @GetMapping
    public ApiResponse<AuditLogPageResponse> search(
        @RequestParam(required = false) Long store_id,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
        @RequestParam(required = false) String actor,
        @RequestParam(required = false) String action,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "50") int size
    ) {
        var user = store_id == null
            ? authorizationService.requireOwner(Capability.ADMIN_USER_ROLE_MANAGE)
            : authorizationService.requireManagerOrOwnerForStore(store_id, Capability.ADMIN_USER_ROLE_MANAGE);
        Long effectiveStoreId = authorizationService.isOwner(user) ? store_id : user.storeId();
        return ApiResponse.success(auditLogService.search(effectiveStoreId, date, actor, action, page, size));
    }
}
