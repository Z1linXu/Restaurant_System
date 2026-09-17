package com.restaurant.system.integration.ubereats.service;

import com.restaurant.system.audit.service.AuditLogService;
import com.restaurant.system.common.auth.AuthenticatedUser;
import com.restaurant.system.common.auth.ForbiddenException;
import com.restaurant.system.integration.ubereats.config.UberEatsProperties;
import com.restaurant.system.integration.ubereats.entity.*;
import com.restaurant.system.integration.ubereats.mapping.UberEatsMenuMappingService;
import com.restaurant.system.integration.ubereats.repository.*;
import com.restaurant.system.user.repository.StoreRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Service
public class UberEatsConfigurationService {
    private final UberEatsProperties config;
    private final UberEatsOrderTransactions tx;
    private final UberEatsOrderRepository orders;
    private final UberEatsStoreMappingRepository stores;
    private final UberEatsMenuMappingRepository mappings;
    private final UberEatsEventRepository events;
    private final StoreRepository localStores;
    private final UberEatsMenuMappingService menu;
    private final AuditLogService audit;

    public UberEatsConfigurationService(
            UberEatsProperties config,
            UberEatsStoreMappingRepository stores,
            UberEatsMenuMappingRepository mappings,
            UberEatsEventRepository events,
            StoreRepository localStores,
            UberEatsMenuMappingService menu,
            AuditLogService audit,
            UberEatsOrderTransactions tx,
            UberEatsOrderRepository orders) {
        this.orders = orders;
        this.tx = tx;
        this.config = config;
        this.stores = stores;
        this.mappings = mappings;
        this.events = events;
        this.localStores = localStores;
        this.menu = menu;
        this.audit = audit;
    }

    public record Connection(
            boolean enabled,
            String environment,
            UberEatsStoreMapping store,
            String webhook_status,
            LocalDateTime last_event_at,
            List<UberEatsMenuMapping> mappings,
            long unmapped_orders) {}

    public Connection connection(Long storeId) {
        var binding = stores.findByEnvironmentAndStoreId(config.environment, storeId).orElse(null);
        var event =
                binding == null
                        ? null
                        : events.findFirstByEnvironmentAndUberStoreIdOrderByIdDesc(
                                        config.environment, binding.uberStoreId)
                                .orElse(null);
        return new Connection(
                config.enabled,
                config.environment,
                binding,
                event == null ? "NOT_OBSERVED" : "RECEIVED",
                event == null ? null : event.createdAt,
                binding == null
                        ? List.of()
                        : mappings.findAllByStoreMappingIdOrderByIdAsc(binding.id),
                orders.countByEnvironmentAndStoreIdAndMappingStatus(
                        config.environment, storeId, "MAPPING_REQUIRED"));
    }

    // The shared application credential can cover several tenants. Only platform ADMIN may
    // establish the external Store ownership binding.
    @Transactional
    public UberEatsStoreMapping bind(Long storeId, String uberId, AuthenticatedUser actor) {
        if (!"ADMIN".equals(actor.roleCode()))
            throw new ForbiddenException("Platform ADMIN must establish Uber Store ownership");
        try {
            uberId = UUID.fromString(uberId).toString();
        } catch (Exception ex) {
            throw UberEatsException.conflict("UBER_STORE_ID_INVALID");
        }
        var local =
                localStores
                        .findById(storeId)
                        .orElseThrow(() -> UberEatsException.conflict("LOCAL_STORE_MISSING"));
        var existing =
                stores.findByEnvironmentAndUberStoreId(config.environment, uberId).orElse(null);
        if (existing != null) {
            if (!storeId.equals(existing.storeId)
                    || !Objects.equals(local.organization_id, existing.organizationId))
                throw UberEatsException.conflict("UBER_STORE_ALREADY_BOUND");
            return existing;
        }
        if (stores.findByEnvironmentAndStoreId(config.environment, storeId).isPresent())
            throw UberEatsException.conflict("LOCAL_STORE_ALREADY_BOUND");
        var row = new UberEatsStoreMapping();
        row.environment = config.environment;
        row.uberStoreId = uberId;
        row.storeId = storeId;
        row.organizationId = local.organization_id;
        row.enabled = true;
        row.createdAt = LocalDateTime.now();
        stores.save(row);
        audit.record(
                storeId,
                actor,
                "UBER_STORE_MAPPED",
                "UBER_STORE",
                row.id,
                "Uber Store linked",
                Map.of("uber_store_id", uberId),
                null);
        return row;
    }

    @Transactional
    public UberEatsMenuMapping saveMapping(
            Long storeId, UberEatsMenuMapping request, AuthenticatedUser actor) {
        var binding =
                stores.findByEnvironmentAndStoreId(config.environment, storeId)
                        .orElseThrow(() -> UberEatsException.conflict("STORE_MAPPING_MISSING"));
        var local = localStores.findById(storeId).orElseThrow();
        if (!Objects.equals(local.organization_id, binding.organizationId))
            throw UberEatsException.conflict("STORE_MAPPING_INVALID");
        if (!Set.of("ITEM", "MODIFIER", "REMOVED_MODIFIER").contains(empty(request.kind))
                || !Set.of("ID", "EXTERNAL_DATA").contains(empty(request.identifierType))
                || empty(request.uberIdentifier).isBlank()
                || request.uberIdentifier.length() > 255)
            throw UberEatsException.conflict("MAPPING_KEY_INVALID");
        request.uberItemId = "ITEM".equals(request.kind) ? "" : empty(request.uberItemId);
        if (!"ITEM".equals(request.kind) && request.uberItemId.isBlank())
            throw UberEatsException.conflict("UBER_PARENT_ITEM_REQUIRED");
        var catalog = menu.catalog(storeId);
        var target =
                menu.allItems(catalog).stream()
                        .filter(i -> i.id.equals(request.localMenuItemId))
                        .findFirst()
                        .orElseThrow(() -> UberEatsException.conflict("LOCAL_ITEM_NOT_IN_STORE"));
        if (!"ITEM".equals(request.kind)) {
            var choices =
                    menu.choices(catalog, target).stream()
                            .filter(
                                    c ->
                                            Objects.equals(c.code(), request.localOptionCode)
                                                    && Objects.equals(
                                                            c.group(), request.localOptionGroup)
                                                    && empty(c.parentCode())
                                                            .equals(
                                                                    empty(
                                                                            request.parentOptionCode)))
                            .toList();
            if (choices.size() != 1) throw UberEatsException.conflict("LOCAL_OPTION_NOT_IN_ITEM");
            if ("REMOVED_MODIFIER".equals(request.kind)
                    && !Set.of("REMOVE", "COMBO_SIDE_REMOVE").contains(request.localOptionGroup))
                throw UberEatsException.conflict("REMOVED_MODIFIER_MUST_MAP_TO_REMOVE");
        } else {
            request.localOptionCode = null;
            request.localOptionGroup = null;
            request.parentOptionCode = null;
        }
        var existing =
                mappings.findAllByStoreMappingIdOrderByIdAsc(binding.id).stream()
                        .filter(
                                m ->
                                        m.kind.equals(request.kind)
                                                && m.identifierType.equals(request.identifierType)
                                                && m.uberItemId.equals(request.uberItemId)
                                                && m.uberIdentifier.equals(request.uberIdentifier))
                        .findFirst()
                        .orElse(new UberEatsMenuMapping());
        existing.storeMappingId = binding.id;
        existing.kind = request.kind;
        existing.identifierType = request.identifierType;
        existing.uberIdentifier = request.uberIdentifier;
        existing.uberItemId = request.uberItemId;
        existing.localMenuItemId = request.localMenuItemId;
        existing.localOptionCode = request.localOptionCode;
        existing.localOptionGroup = request.localOptionGroup;
        existing.parentOptionCode = request.parentOptionCode;
        existing.updatedAt = LocalDateTime.now();
        mappings.saveAndFlush(existing);
        tx.remapStore(storeId);
        audit.record(
                storeId,
                actor,
                "UBER_MENU_MAPPED",
                "UBER_MAPPING",
                existing.id,
                "Uber stable identifier mapped",
                Map.of("kind", request.kind, "local_menu_item_id", request.localMenuItemId),
                null);
        return existing;
    }

    private static String empty(String s) {
        return s == null ? "" : s;
    }
}
