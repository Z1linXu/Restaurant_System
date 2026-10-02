package com.restaurant.system.integration.ubereats.service;

import java.time.*;

/** Remote placement/new release timestamps are UTC; legacy creation timestamps are JVM-local. */
public record UberEatsInboxDay(
        LocalDateTime start,
        LocalDateTime end,
        LocalDateTime createdStart,
        LocalDateTime createdEnd) {
    public static UberEatsInboxDay of(LocalDate day, ZoneId businessZone, ZoneId serverZone) {
        var start = day.atStartOfDay(businessZone);
        var end = day.plusDays(1).atStartOfDay(businessZone);
        return new UberEatsInboxDay(
                start.withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime(),
                end.withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime(),
                start.withZoneSameInstant(serverZone).toLocalDateTime(),
                end.withZoneSameInstant(serverZone).toLocalDateTime());
    }
}
