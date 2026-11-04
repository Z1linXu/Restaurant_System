package com.restaurant.system.integration.ubereats.controller;

import com.restaurant.system.audit.service.AuditLogService;
import com.restaurant.system.common.auth.*;
import com.restaurant.system.common.response.ApiResponse;
import com.restaurant.system.common.exception.BusinessException;
import com.restaurant.system.integration.ubereats.config.UberEatsProperties;
import com.restaurant.system.integration.ubereats.dto.UberOrderSnapshot;
import com.restaurant.system.integration.ubereats.entity.*;
import com.restaurant.system.integration.ubereats.mapping.UberEatsMenuMappingService;
import com.restaurant.system.integration.ubereats.repository.UberEatsOrderRepository;
import com.restaurant.system.integration.ubereats.repository.UberEatsInboxReadRepository;
import com.restaurant.system.user.repository.StoreRepository;
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
    private final UberEatsKitchenView kitchen;
    private final UberEatsInboxReadRepository reads;
    private final StoreRepository stores;

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
            AuditLogService audit,
            UberEatsKitchenView kitchen,
            UberEatsInboxReadRepository reads,
            StoreRepository stores) {
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
        this.kitchen = kitchen;
        this.reads = reads;
        this.stores = stores;
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
            UberOrderSnapshot snapshot,
            String processing_mode,
            String customer_header,
            LocalDateTime released_at,
            List<String> mapped_items,
            String grab_status,
            String hot_kitchen_status,
            LocalDateTime accepted_observed_at, List<String> raw_items, String order_note_snapshot) {}

    private AuthenticatedUser staff(Long storeId, Capability capability) {
        var actor = auth.requireFrontdeskAccessForStore(storeId, capability);
        access.requireStoreAccess(actor, storeId);
        modules.requireCapability(storeId, ModuleKeys.UBER_EATS);
        return actor;
    }

    private AuthenticatedUser admin(Long storeId) {
        var actor = auth.requireForStore(storeId, Capability.ADMIN_MENU_MANAGE);
        access.requireStoreAccess(actor, storeId);
        if (!Set.of("OWNER", "ADMIN").contains(actor.roleCode()))
            throw new ForbiddenException("Owner or Admin required");
        modules.requireCapability(storeId, ModuleKeys.UBER_EATS);
        return actor;
    }

    @GetMapping("/orders")
    public ApiResponse<List<InboxOrder>> inbox(@PathVariable Long storeId) {
        staff(storeId, Capability.ORDER_VIEW_ACTIVE);
        var zone = java.time.ZoneId.of(config.businessTimeZone);
        var today = java.time.LocalDate.now(zone);
        var day = UberEatsInboxDay.of(today, zone, java.time.ZoneId.systemDefault());
        var rows=orders.todayInbox(config.environment,storeId,day.start(),day.end(),
                day.createdStart(),day.createdEnd(),PageRequest.of(0,200));
        var states=kitchen.states(rows);
        return ApiResponse.success(rows.stream().map(row->view(row,states.get(row))).toList());
    }

    public record InboxPage(List<InboxOrder> orders, boolean has_more, Long next_before_id) {}
    public record Inbox(String business_date,String time_zone,InboxPage today,InboxPage attention,InboxPage history) {}

    /** Separate current-day work, unresolved attention, and explicitly requested history. */
    @GetMapping("/inbox")
    public ApiResponse<Inbox> groupedInbox(@PathVariable Long storeId,
            @RequestParam(name="limit",required=false) String limitValue,
            @RequestParam(name="today_before_id",required=false) String todayBeforeValue,
            @RequestParam(name="attention_before_id",required=false) String attentionBeforeValue,
            @RequestParam(name="history_date",required=false) String historyDateValue,
            @RequestParam(name="history_before_id",required=false) String historyBeforeValue) {
        staff(storeId,Capability.ORDER_VIEW_ACTIVE);
        int limit=parseLimit(limitValue);
        Long today_before_id=parseCursor(todayBeforeValue);
        Long attention_before_id=parseCursor(attentionBeforeValue);
        Long history_before_id=parseCursor(historyBeforeValue);
        java.time.LocalDate history_date=parseHistoryDate(historyDateValue);
        if (history_before_id!=null && history_date==null) throw invalidInboxPage();
        String timezone=stores.findById(storeId).map(store->store.timezone).filter(value->!value.isBlank()).orElse(config.businessTimeZone);
        var zone=java.time.ZoneId.of(timezone);
        var today=java.time.LocalDate.now(zone);
        var day=UberEatsInboxDay.of(today,zone,java.time.ZoneId.systemDefault());
        var current=reads.page(config.environment,storeId,day,"today",today_before_id,limit);
        var attention=reads.page(config.environment,storeId,day,"attention",attention_before_id,limit);
        var historical=history_date==null?null:reads.page(config.environment,storeId,
            UberEatsInboxDay.of(history_date,zone,java.time.ZoneId.systemDefault()),"history",history_before_id,limit);
        List<UberEatsOrder> rows=new ArrayList<>(current.rows()); rows.addAll(attention.rows());
        if (historical!=null) rows.addAll(historical.rows());
        var states=kitchen.states(rows);
        return ApiResponse.success(new Inbox(today.toString(),timezone,page(current,states),page(attention,states),
            historical==null?null:page(historical,states)));
    }

    private int parseLimit(String value) {
        if (value==null) return 100;
        Long limit=parseCursor(value);
        if (limit>200) throw invalidInboxPage();
        return limit.intValue();
    }

    private Long parseCursor(String value) {
        if (value==null) return null;
        if (value.length()>19 || !value.matches("[0-9]+")) throw invalidInboxPage();
        try {
            long cursor=Long.parseLong(value);
            if (cursor<=0) throw invalidInboxPage();
            return cursor;
        } catch (NumberFormatException invalid) {
            throw invalidInboxPage();
        }
    }

    private java.time.LocalDate parseHistoryDate(String value) {
        if (value==null) return null;
        if (!value.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) throw invalidInboxPage();
        try {
            var day=java.time.LocalDate.parse(value);
            if (day.getYear()<1) throw invalidInboxPage();
            return day;
        } catch (java.time.format.DateTimeParseException invalid) {
            throw invalidInboxPage();
        }
    }

    private BusinessException invalidInboxPage() { return new BusinessException("INVALID_INBOX_PAGE"); }
    private InboxPage page(UberEatsInboxReadRepository.Page page,Map<UberEatsOrder,UberEatsKitchenView.State> states) {
        return new InboxPage(page.rows().stream().map(row->view(row,states.get(row))).toList(),page.hasMore(),page.nextBeforeId());
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

    public record ProcessingModeRequest(String processing_mode) {}

    @PutMapping("/processing-mode")
    public ApiResponse<UberEatsStoreMapping> processingMode(
            @PathVariable Long storeId, @RequestBody ProcessingModeRequest request) {
        return ApiResponse.success(
                configuration.setMode(storeId, request.processing_mode(), admin(storeId)));
    }

    @PutMapping("/mappings")
    public ApiResponse<UberEatsMenuMapping> mapping(
            @PathVariable Long storeId, @RequestBody UberEatsMenuMapping request) {
        var actor = admin(storeId);
        return ApiResponse.success(configuration.saveMapping(storeId, request, actor));
    }

    @PostMapping("/mapping-preview")
    public ApiResponse<UberEatsMenuMappingService.Result> mappingPreview(
            @PathVariable Long storeId,
            @RequestBody com.fasterxml.jackson.databind.JsonNode source) {
        admin(storeId);
        var binding = configuration.connection(storeId).store();
        if (binding == null) throw UberEatsException.conflict("STORE_MAPPING_MISSING");
        tx.requireMapping(binding.id);
        if (source.toString().length() > 262144)
            throw UberEatsException.conflict("MAPPING_PREVIEW_TOO_LARGE");
        var snapshot = new UberEatsOrderNormalizer().normalize(source);
        return ApiResponse.success("KITCHEN_MIRROR".equals(binding.processingMode)
                ? menu.mapMirror(binding, snapshot) : menu.map(binding, snapshot));
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
    private InboxOrder view(UberEatsOrder row) { return view(row,kitchen.state(row)); }

    @SuppressWarnings("unchecked")
    private InboxOrder view(UberEatsOrder row,UberEatsKitchenView.State print) {
        var content=kitchen.content(row);
        return new InboxOrder(
                row.id,
                print.status(),
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
                        : tx.decode(row.rawOrderSnapshotJson, UberOrderSnapshot.class),
                row.processingMode,
                com.restaurant.system.printing.renderer.ExternalOrderReceiptHeader.kitchenLabel(
                        row.customerDisplayName, row.displayId),
                row.releasedAt,
                content.mappedItems(),
                print.grab(),
                print.hotKitchen(),
                row.acceptedObservedAt, content.rawItems(), content.orderNote());
    }
}
