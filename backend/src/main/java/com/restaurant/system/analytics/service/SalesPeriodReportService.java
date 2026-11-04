package com.restaurant.system.analytics.service;

import com.restaurant.system.analytics.dto.SalesPeriodReport;
import com.restaurant.system.analytics.support.SalesReporting;
import com.restaurant.system.common.exception.BusinessException;
import com.restaurant.system.order.repository.OrderRepository;
import com.restaurant.system.user.entity.Store;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/** Day/month views reuse the deployed channel policy, including the latest valid correction journal.
 * No persisted summary can authorize removal of the original orders, snapshots or deduplication keys.
 */
@Service
public class SalesPeriodReportService {
    private final ChannelSalesService channels;
    private final OrderRepository orders;

    public SalesPeriodReportService(ChannelSalesService channels, OrderRepository orders) {
        this.channels = channels;
        this.orders = orders;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public SalesPeriodReport build(Store store, String period, LocalDate anchor) {
        if (store == null || store.id == null || anchor == null)
            throw new BusinessException("A store and report date are required");
        String kind = period == null ? "" : period.toLowerCase(Locale.ROOT);
        LocalDate start, end;
        if ("day".equals(kind)) {
            start = anchor;
            end = anchor;
        } else if ("month".equals(kind)) {
            YearMonth month = YearMonth.from(anchor);
            start = month.atDay(1);
            end = month.atEndOfMonth();
        } else throw new BusinessException("period must be day or month");
        String zone = store.timezone == null || store.timezone.isBlank() ? "America/Toronto" : store.timezone;
        try { ZoneId.of(zone); }
        catch (RuntimeException invalidZone) { throw new BusinessException("Store reporting timezone is invalid"); }

        // Recompute, rather than increment, so retries, late submissions and APPLY/REVOKE do not double count.
        var summary = channels.build(List.of(store.id), start, end, false);
        var metric = summary.totals.total;
        var pending = new SalesPeriodReport.Pending(metric.unknown_order_count, metric.unknown_line_count,
                metric.unknown_order_count == 0 ? BigDecimal.ZERO.setScale(2) : null,
                metric.unknown_order_count == 0 ? "NONE" : "UNKNOWN");

        var startAt = start.atStartOfDay();
        var endAt = end.plusDays(1).atStartOfDay();
        var pos = orders.findAllByStoreId(store.id).stream()
                .filter(row -> store.id.equals(row.store_id))
                .filter(SalesReporting::eligible)
                .filter(row -> !row.submitted_at.isBefore(startAt) && row.submitted_at.isBefore(endAt))
                .toList();
        boolean discountKnown = pos.stream().allMatch(row -> row.discount_amount != null);
        BigDecimal discount = discountKnown
                ? pos.stream().map(row -> row.discount_amount).reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add)
                : null;
        var adjustments = new SalesPeriodReport.Adjustments(discount,
                discountKnown ? "RECORDED_ORDER_DISCOUNT" : "UNKNOWN",
                null, "UNKNOWN_NO_SEPARATE_VERIFIED_TAX_LEDGER",
                null, "UNKNOWN_NO_SEPARATE_VERIFIED_DISCOUNT_LEDGER",
                null, "UNKNOWN_NO_VERIFIED_REFUND_LEDGER",
                "Informational only. Sales already retain their existing tax/discount basis; do not add or subtract these values again. Unknown adjustments are not zero.");
        return new SalesPeriodReport(store.id, zone, kind, start, end,
                "LIVE_RECOMPUTED_NOT_ARCHIVE", true, summary, pending, adjustments);
    }
}
