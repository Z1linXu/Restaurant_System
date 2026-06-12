package com.restaurant.system.analytics.repository;

import com.restaurant.system.analytics.entity.SalesHourlySummary;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SalesHourlySummaryRepository extends JpaRepository<SalesHourlySummary, Long> {
    @Query(
        value = """
            select *
            from sales_hourly_summary
            where store_id = :storeId
              and summary_date = :summaryDate
            order by hour_of_day asc
            """,
        nativeQuery = true
    )
    List<SalesHourlySummary> findAllByStore_idAndSummary_dateOrderByHour_of_dayAsc(
        @Param("storeId") Long storeId,
        @Param("summaryDate") LocalDate summaryDate
    );

    @Query(
        value = """
            select *
            from sales_hourly_summary
            where organization_id = :organizationId
              and summary_date = :summaryDate
            order by hour_of_day asc
            """,
        nativeQuery = true
    )
    List<SalesHourlySummary> findAllByOrganization_idAndSummary_dateOrderByHour_of_dayAsc(
        @Param("organizationId") Long organizationId,
        @Param("summaryDate") LocalDate summaryDate
    );

    @Modifying
    @Query(
        value = """
            delete from sales_hourly_summary
            where summary_date = :summaryDate
              and store_id = :storeId
            """,
        nativeQuery = true
    )
    void deleteAllBySummary_dateAndStore_id(@Param("summaryDate") LocalDate summaryDate, @Param("storeId") Long storeId);
}
