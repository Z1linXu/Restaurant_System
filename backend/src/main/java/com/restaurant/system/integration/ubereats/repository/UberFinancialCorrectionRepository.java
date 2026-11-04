package com.restaurant.system.integration.ubereats.repository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class UberFinancialCorrectionRepository {
    public record Entry(long id, long orderId, String action, String originalHash, String sourceHash, String snapshotJson) {}
    private final JdbcTemplate jdbc;
    public UberFinancialCorrectionRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    /** One bounded read per Store/window; original frozen snapshots are never changed. */
    public Map<Long, Entry> latest(String environment, Long storeId, List<Long> orderIds) {
        if (orderIds.isEmpty()) return Map.of();
        String placeholders = String.join(",", Collections.nCopies(orderIds.size(), "?"));
        List<Object> args = new ArrayList<>(List.of(environment, storeId));
        args.addAll(orderIds);
        List<Entry> entries = jdbc.query("""
                SELECT id, uber_eats_order_id, action, original_snapshot_sha256, source_charges_sha256, snapshot_json
                FROM uber_eats_financial_corrections WHERE environment=? AND store_id=?
                AND uber_eats_order_id IN (""" + placeholders + ") ORDER BY id DESC",
                (rs, n) -> new Entry(rs.getLong(1), rs.getLong(2), rs.getString(3),
                        rs.getString(4).trim(), rs.getString(5).trim(), rs.getString(6)), args.toArray());
        Map<Long, Entry> result = new HashMap<>();
        entries.forEach(entry -> result.putIfAbsent(entry.orderId(), entry));
        return result;
    }

    public static String sha256(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest((value == null ? "" : value).getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
}
