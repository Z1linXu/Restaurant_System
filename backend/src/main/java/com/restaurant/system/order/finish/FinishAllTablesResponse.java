package com.restaurant.system.order.finish;

import java.util.List;

public final class FinishAllTablesResponse {
    private FinishAllTablesResponse() {}
    public record Target(Long order_id, String table_no, String table_display_name, String fingerprint) {}
    public record Preview(String batch_id, Long store_id, String store_name, long expires_at,
            List<Target> orders, int order_count, int table_slot_count,
            List<String> blocked_tables, String preview_token) {}
    public record Plan(String batch_id, Long store_id, Long actor_user_id, long expires_at, List<Target> orders) {}
    public record Request(String preview_token) {}
    public record Outcome(Long order_id, String table_no, String outcome, String reason) {}
    public record Result(String batch_id, int completed, int skipped, int failed,
            int released_slot_count, List<Outcome> results, List<String> remaining_tables) {}
}
