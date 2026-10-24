package com.restaurant.system.station.service;

import com.restaurant.system.common.exception.BusinessException;
import com.restaurant.system.order.entity.Order;
import com.restaurant.system.station.entity.DiningTable;
import com.restaurant.system.station.repository.DiningTableRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/** Display-only projection. Raw table_no remains the order/occupancy identity. */
@Service
public class DiningTableDisplayService {
    private final DiningTableRepository tables;
    private final EntityManager entityManager;

    public DiningTableDisplayService(DiningTableRepository tables, EntityManager entityManager) {
        this.tables = tables;
        this.entityManager = entityManager;
    }

    public String displayName(Long storeId, String rawTableNo) {
        if (rawTableNo == null || rawTableNo.isBlank()) return rawTableNo;
        String raw = rawTableNo.trim();
        if (storeId == null) return legacyDisplayName(raw);
        // A literal table named "1-A" or "1里" is a whole table, never a guessed seat.
        if (!tables.findAllByStoreIdAndTableName(storeId, raw).isEmpty()) return raw;
        String base = splitBase(raw);
        if (base == null) return legacyDisplayName(raw);
        List<DiningTable> matches = tables.findAllByStoreIdAndTableName(storeId, base);
        if (matches.size() != 1) return legacyDisplayName(raw);
        boolean seatA = raw.endsWith("-A");
        return "INSIDE_OUTSIDE".equals(matches.get(0).split_label_mode)
            ? base + (seatA ? "里" : "外")
            : base + (seatA ? "-左" : "-右");
    }

    public void apply(Order order) {
        if (order == null) return;
        order.table_display_name = "dine_in".equalsIgnoreCase(order.order_type) && !order.kitchenMirror()
            ? displayName(order.store_id, order.table_no) : order.table_no;
    }

    /** Called inside the order transaction, before acquiring any Order row lock. */
    public void lockContexts(Long storeId, String... rawTableNos) {
        if (storeId == null) return;
        Map<Long, String> identities = new LinkedHashMap<>();
        for (String raw : rawTableNos) {
            List<DiningTable> matches = contextTables(storeId, raw);
            for (DiningTable table : matches) identities.put(table.id, tableName(table));
        }
        List<Long> ids = new ArrayList<>(identities.keySet());
        ids.sort(Comparator.naturalOrder());
        for (Long id : ids) {
            DiningTable locked = tables.findByIdAndStoreIdForUpdate(id, storeId);
            if (locked == null) throw new BusinessException("TABLE_CONTEXT_CHANGED: Refresh before continuing");
            // OSIV may contain the row read before another transaction changed its mode.
            entityManager.refresh(locked, LockModeType.PESSIMISTIC_WRITE);
            if (!identities.get(id).equals(tableName(locked))) {
                throw new BusinessException("TABLE_CONTEXT_CHANGED: Refresh before continuing");
            }
        }
    }

    private List<DiningTable> contextTables(Long storeId, String rawTableNo) {
        if (rawTableNo == null || rawTableNo.isBlank()) return List.of();
        String raw = rawTableNo.trim();
        List<DiningTable> exact = tables.findAllByStoreIdAndTableName(storeId, raw);
        if (!exact.isEmpty()) return unambiguous(exact);
        String base = splitBase(raw);
        if (base != null) {
            List<DiningTable> split = tables.findAllByStoreIdAndTableName(storeId, base);
            if (!split.isEmpty()) return unambiguous(split);
        }
        // Legacy clients may still submit table_code. Lock every matching row conservatively;
        // this does not interpret that code as a display name or change the raw identity.
        List<DiningTable> legacy = tables.findAllByStoreIdAndTableCode(storeId, raw);
        return !legacy.isEmpty() || base == null ? legacy : tables.findAllByStoreIdAndTableCode(storeId, base);
    }

    private List<DiningTable> unambiguous(List<DiningTable> matches) {
        if (matches.size() > 1) throw new BusinessException("AMBIGUOUS_TABLE_CONTEXT: Refresh the table configuration");
        return matches;
    }

    private String tableName(DiningTable table) {
        return table.table_name == null || table.table_name.isBlank() ? table.table_code : table.table_name;
    }

    private static String splitBase(String raw) {
        return raw.length() > 2 && (raw.endsWith("-A") || raw.endsWith("-B"))
            ? raw.substring(0, raw.length() - 2) : null;
    }

    public static String legacyDisplayName(String value) {
        if (value == null) return null;
        String raw = value.trim();
        if ("A".equalsIgnoreCase(raw)) return "左";
        if ("B".equalsIgnoreCase(raw)) return "右";
        String upper = raw.toUpperCase(java.util.Locale.ROOT);
        return splitBase(upper) == null ? raw
            : raw.substring(0, raw.length() - 2) + (upper.endsWith("-A") ? "-左" : "-右");
    }
}
