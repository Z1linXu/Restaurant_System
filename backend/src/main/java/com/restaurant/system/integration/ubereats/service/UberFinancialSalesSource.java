package com.restaurant.system.integration.ubereats.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurant.system.analytics.service.UberSalesSource;
import com.restaurant.system.integration.ubereats.config.UberEatsProperties;
import com.restaurant.system.integration.ubereats.dto.UberFinancialSnapshot;
import com.restaurant.system.integration.ubereats.entity.UberEatsOrder;
import com.restaurant.system.integration.ubereats.repository.UberEatsOrderRepository;
import com.restaurant.system.user.repository.StoreRepository;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Environment-scoped, read-only sales. No menu lookup, remote API, order replay or price fallback. */
@Service
public class UberFinancialSalesSource implements UberSalesSource {
    private final UberEatsOrderRepository orders;
    private final StoreRepository stores;
    private final UberEatsProperties config;
    private final ObjectMapper json;
    private final com.restaurant.system.integration.ubereats.repository.UberFinancialCorrectionRepository corrections;

    public UberFinancialSalesSource(UberEatsOrderRepository orders, StoreRepository stores,
            UberEatsProperties config, ObjectMapper json,
            com.restaurant.system.integration.ubereats.repository.UberFinancialCorrectionRepository corrections) {
        this.orders = orders; this.stores = stores; this.config = config; this.json = json; this.corrections = corrections;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Sale> load(List<Long> storeIds, LocalDateTime start, LocalDateTime end) {
        List<Sale> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (Long storeId : new LinkedHashSet<>(storeIds)) {
            var store = stores.findById(storeId).orElse(null);
            if (store == null) continue;
            ZoneId zone = ZoneId.of(store.timezone == null || store.timezone.isBlank()
                    ? "America/Toronto" : store.timezone);
            LocalDateTime utcStart = LocalDateTime.ofInstant(start.atZone(zone).toInstant(), ZoneOffset.UTC);
            LocalDateTime utcEnd = LocalDateTime.ofInstant(end.atZone(zone).toInstant(), ZoneOffset.UTC);
            var rows = orders.salesWindow(config.environment, storeId, utcStart, utcEnd);
            var revisions = corrections.latest(config.environment, storeId, rows.stream().map(row -> row.id).filter(Objects::nonNull).toList());
            for (UberEatsOrder row : rows) {
                if (!storeId.equals(row.storeId) || !config.environment.equals(row.environment)
                        || Boolean.TRUE.equals(row.cancelled) || Boolean.TRUE.equals(row.editRequired)
                        || Boolean.TRUE.equals(row.scheduled) || row.placedAt == null
                        || Set.of("DENIED", "CANCELLED", "CANCELLED_AFTER_RELEASE").contains(row.status)
                        || row.acceptedObservedAt == null && row.acceptedAt == null && row.localOrderId == null)
                    continue;
                LocalDateTime placed = LocalDateTime.ofInstant(row.placedAt.toInstant(ZoneOffset.UTC), zone);
                if (placed.isBefore(start) || !placed.isBefore(end) || !seen.add(row.environment + ":" + row.uberOrderId)) continue;
                result.add(sale(row, placed, revisions.get(row.id)));
            }
        }
        return List.copyOf(result);
    }

    private Sale sale(UberEatsOrder row, LocalDateTime placed,
            com.restaurant.system.integration.ubereats.repository.UberFinancialCorrectionRepository.Entry revision) {
        JsonNode raw = tree(row.rawOrderSnapshotJson);
        JsonNode rootItems = raw.path("items");
        JsonNode local = tree(row.localRequestJson);
        JsonNode localItems = local.path("items");
        UberFinancialSnapshot financial = null;
        try {
            if (row.itemFinancialSnapshotJson != null)
                financial = json.readValue(row.itemFinancialSnapshotJson, UberFinancialSnapshot.class);
        } catch (Exception ignored) { /* Incomplete evidence yields unknown money, never zero. */ }
        if (revision != null && "APPLY".equals(revision.action())) {
            try {
                if (revision.originalHash().equals(com.restaurant.system.integration.ubereats.repository.UberFinancialCorrectionRepository.sha256(row.itemFinancialSnapshotJson))
                        && revision.sourceHash().equals(com.restaurant.system.integration.ubereats.repository.UberFinancialCorrectionRepository.sha256(row.rawFinancialSnapshotJson)))
                    {
                    financial = json.readValue(revision.snapshotJson(), UberFinancialSnapshot.class);
                    if (financial.version() != 2) financial = null;
                }
                else financial = null; // Changed evidence must not silently reuse a correction.
            } catch (Exception invalid) { financial = null; }
        }
        boolean same = financial != null && (financial.version() == 1 || financial.version() == 2)
                && row.uberOrderId.equals(financial.order_id()) && row.uberStoreId.equals(financial.store_id())
                && "CAD".equals(financial.currency()) && financial.items() != null
                && rootItems.isArray() && rootItems.size() == financial.items().size();
        if (same) for (int n = 0; n < rootItems.size(); n++) {
            var line = financial.items().get(n);
            if (!rootItems.get(n).path("id").asText().equals(line.id())
                    || rootItems.get(n).path("quantity").asInt() != line.quantity()) { same = false; break; }
        }
        boolean moneyKnown = same && financial.revenue_minor() != null && financial.blocked_reason() == null;
        boolean localIdentity = rootItems.isArray() && localItems.isArray() && rootItems.size() == localItems.size()
                && local.path("store_id").asLong(-1) == row.storeId;
        List<Item> items = new ArrayList<>();
        if (rootItems.isArray()) for (int n = 0; n < rootItems.size(); n++) {
            JsonNode source = rootItems.get(n);
            if (source.path("removed").asBoolean()) continue;
            int quantity = source.path("quantity").asInt(0);
            if (quantity < 1 || quantity > 100) continue;
            JsonNode identity = localIdentity ? localItems.get(n) : json.createObjectNode();
            boolean mapped = identity.path("quantity").asInt() == quantity
                    && !identity.path("external_kitchen_snapshot").path("rawRoot").asBoolean()
                    && !identity.path("item_sku_snapshot").asText("").isBlank();
            items.add(new Item(source.path("id").asText(), mapped ? identity.path("item_sku_snapshot").asText() : null,
                    mapped ? identity.path("category_code_snapshot").asText() : "OTHER",
                    mapped ? identity.path("item_name_snapshot_en").asText() : source.path("title").asText("Uber item"),
                    quantity, moneyKnown ? financial.items().get(n).revenue_minor() : null));
        }
        return new Sale(row.storeId, row.uberOrderId, placed, same ? financial.currency() : null,
                moneyKnown ? financial.revenue_minor() : null, List.copyOf(items), moneyKnown ? null
                        : financial == null ? "FINANCIAL_EVIDENCE_MISSING"
                        : !same ? "FINANCIAL_IDENTITY_MISMATCH"
                        : financial.blocked_reason() == null ? "FINANCIAL_AMOUNT_MISSING" : financial.blocked_reason());
    }

    private JsonNode tree(String value) {
        try { return value == null ? json.createObjectNode() : json.readTree(value); }
        catch (Exception ignored) { return json.createObjectNode(); }
    }
}
