package com.restaurant.system.order.close;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class StoreDailyCloseRunRepository {
    private final JdbcTemplate jdbc;

    public StoreDailyCloseRunRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    // The unique Store/date insert serializes concurrent workers. The row and all Finish
    // operations commit together; a failed batch leaves no reservation and can retry.
    public boolean reserve(Long storeId, LocalDate date, String timezone, Instant now) {
        return jdbc.update("""
            insert into store_daily_close_runs(store_id, business_date, timezone, started_at)
            values (?, ?, ?, ?) on conflict (store_id, business_date) do nothing
            """, storeId, date, timezone, OffsetDateTime.ofInstant(now, ZoneOffset.UTC)) == 1;
    }

    public void complete(Long storeId, LocalDate date, Instant now, int count) {
        jdbc.update("""
            update store_daily_close_runs set completed_at = ?, finished_order_count = ?
            where store_id = ? and business_date = ?
            """, OffsetDateTime.ofInstant(now, ZoneOffset.UTC), count, storeId, date);
    }
}
