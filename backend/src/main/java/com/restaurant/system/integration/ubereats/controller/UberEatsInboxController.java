package com.restaurant.system.integration.ubereats.controller;

import com.restaurant.system.audit.service.AuditLogService;
import com.restaurant.system.common.auth.*;
import com.restaurant.system.common.response.ApiResponse;
import com.restaurant.system.integration.ubereats.config.UberEatsProperties;
import com.restaurant.system.integration.ubereats.dto.UberOrderSnapshot;
import com.restaurant.system.integration.ubereats.entity.*;
import com.restaurant.system.integration.ubereats.mapping.UberEatsMenuMappingService;
import com.restaurant.system.integration.ubereats.repository.UberEatsOrderRepository;
import com.restaurant.system.integration.ubereats.service.*;
import com.restaurant.system.modules.*;

import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/api/v1/stores/{storeId}/integrations/uber-eats")
public class UberEatsInboxController {
    private final AuthorizationService auth;
    private final StoreAccessService access;
    private final UberEatsProperties config;
    private final UberEatsOrderRepository orders;
    private final UberEatsOrderTransactions tx;
    private final UberEatsOrderImportService service;
    private final UberEatsConfigurationService configuration;
    private final UberEatsMenuMappingService menu;
    private final StoreModuleAccessEvaluator modules;
    private final AuditLogService audit;

    public UberEatsInboxController(
            AuthorizationService auth,
            StoreAccessService access,
            UberEatsProperties config,
            UberEatsOrderRepository orders,
            UberEatsOrderTransactions tx,
            UberEatsOrderImportService service,
            UberEatsConfigurationService configuration,
            UberEatsMenuMappingService menu,
            StoreModuleAccessEvaluator modules,
            AuditLogService audit) {
        this.auth = auth;
        this.access = access;
        this.config = config;
        this.orders = orders;
        this.tx = tx;
        this.service = service;
        this.configuration = configuration;
        this.menu = menu;
        this.modules = modules;
        this.audit = audit;
    }

    public record InboxOrder(
            Long id,
            String status,
            String display_id,
            String uber_order_id,
            String mapping_status,
            List<String> mapping_errors,
            String last_error,
            Long local_order_id,
            LocalDateTime placed_at,
            LocalDateTime created_at,
            LocalDateTime accepted_at,
            LocalDateTime cancelled_at,
            UberOrderSnapshot snapshot) {}

    private AuthenticatedUser staff(Long storeId, Capability capability) {
        var actor = auth.requireFrontdeskAccessForStore(storeId, capability);
        access.requireStoreAccess(actor, storeId);
        return actor;
    }

    private AuthenticatedUser admin(Long storeId) {
        var actor = auth.requireForStore(storeId, Capability.ADMIN_MENU_MANAGE);
        access.requireStoreAccess(actor, storeId);
        if (!Set.of("OWNER", "ADMIN").contains(actor.roleCode()))
            throw new ForbiddenException("Owner or Admin required");
        return actor;
    }

    @GetMapping("/orders")
    public ApiResponse<List<InboxOrder>> inbox(@PathVariable Long storeId) {
        staff(storeId, Capability.ORDER_VIEW_ACTIVE);
        return ApiResponse.success(
                orders.inbox(config.environment, storeId, PageRequest.of(0, 100)).stream()
                        .map(this::view)
                        .toList());
    }

    @PostMapping("/orders/{id}/accept")
    public ApiResponse<InboxOrder> accept(@PathVariable Long storeId, @PathVariable Long id) {
        var actor = staff(storeId, Capability.ORDER_SUBMIT);
        modules.requireOperationalCapability(storeId, ModuleKeys.ORDERING_POS);
        return ApiResponse.success(
                view(service.decide(storeId, id, actor.userId(), "ACCEPT", null)));
    }

    public record DenyRequest(String reason_code) {}

    @PostMapping("/orders/{id}/deny")
    public ApiResponse<InboxOrder> deny(
            @PathVariable Long storeId, @PathVariable Long id, @RequestBody DenyRequest request) {
        var actor = staff(storeId, Capability.ORDER_CANCEL);
        if (request.reason_code() == null
                || !Set.of(
                                "STORE_CLOSED",
                                "POS_NOT_READY",
                                "POS_OFFLINE",
                                "ITEM_AVAILABILITY",
                                "MISSING_ITEM",
                                "MISSING_INFO",
                                "PRICING",
                                "CAPACITY",
                                "ADDRESS",
                                "SPECIAL_INSTRUCTIONS",
                                "OTHER")
                        .contains(request.reason_code()))
            throw UberEatsException.conflict("DENY_REASON_INVALID");
        return ApiResponse.success(
                view(service.decide(storeId, id, actor.userId(), "DENY", request.reason_code())));
    }

    @PostMapping("/orders/{id}/retry-local")
    public ApiResponse<InboxOrder> retry(@PathVariable Long storeId, @PathVariable Long id) {
        var actor = staff(storeId, Capability.ORDER_SUBMIT);
        tx.retryLocal(storeId, id);
        audit.record(
                storeId,
                actor,
                "UBER_LOCAL_RETRY",
                "UBER_ORDER",
                id,
                "Retry local production only",
                Map.of(),
                null);
        service.recover();
        return ApiResponse.success(view(tx.scoped(storeId, id)));
    }

    @GetMapping("/connection")
    public ApiResponse<UberEatsConfigurationService.Connection> connection(
            @PathVariable Long storeId) {
        staff(storeId, Capability.ORDER_VIEW_ACTIVE);
        return ApiResponse.success(configuration.connection(storeId));
    }

    public record StoreBindingRequest(String uber_store_id) {}

    @PutMapping("/store")
    public ApiResponse<UberEatsStoreMapping> bind(
            @PathVariable Long storeId, @RequestBody StoreBindingRequest request) {
        var actor = admin(storeId);
        return ApiResponse.success(configuration.bind(storeId, request.uber_store_id(), actor));
    }

    @PutMapping("/mappings")
    public ApiResponse<UberEatsMenuMapping> mapping(
            @PathVariable Long storeId, @RequestBody UberEatsMenuMapping request) {
        var actor = admin(storeId);
        return ApiResponse.success(configuration.saveMapping(storeId, request, actor));
    }

    @GetMapping("/mapping-catalog")
    public ApiResponse<com.restaurant.system.menu.dto.MenuCatalogResponse> mappingCatalog(
            @PathVariable Long storeId) {
        admin(storeId);
        return ApiResponse.success(menu.catalog(storeId));
    }

    @GetMapping("/mapping-options/{itemId}")
    public ApiResponse<List<UberEatsMenuMappingService.Choice>> choices(
            @PathVariable Long storeId, @PathVariable Long itemId) {
        admin(storeId);
        var catalog = menu.catalog(storeId);
        var item =
                menu.allItems(catalog).stream()
                        .filter(i -> i.id.equals(itemId))
                        .findFirst()
                        .orElseThrow(() -> UberEatsException.conflict("LOCAL_ITEM_NOT_IN_STORE"));
        return ApiResponse.success(menu.choices(catalog, item));
    }

    @SuppressWarnings("unchecked")
    private InboxOrder view(UberEatsOrder row) {
        return new InboxOrder(
                row.id,
                row.status,
                row.displayId,
                row.uberOrderId,
                row.mappingStatus,
                row.mappingError == null ? List.of() : tx.decode(row.mappingError, List.class),
                row.lastError,
                row.localOrderId,
                row.placedAt,
                row.createdAt,
                row.acceptedAt,
                row.cancelledAt,
                row.rawOrderSnapshotJson == null
                        ? null
                        : tx.decode(row.rawOrderSnapshotJson, UberOrderSnapshot.class));
    }
}
