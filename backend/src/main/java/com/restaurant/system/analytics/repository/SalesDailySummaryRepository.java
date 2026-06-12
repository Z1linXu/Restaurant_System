package com.restaurant.system.analytics.repository;

import com.restaurant.system.analytics.entity.SalesDailySummary;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SalesDailySummaryRepository extends JpaRepository<SalesDailySummary, Long> {
    @Query(
        value = """
            select *
            from sales_daily_summary
            where summary_date = :summaryDate
              and store_id = :storeId
            limit 1
            """,
        nativeQuery = true
    )
    Optional<SalesDailySummary> findBySummary_dateAndStore_id(@Param("summaryDate") LocalDate summaryDate, @Param("storeId") Long storeId);

    @Query(
        value = """
            select *
            from sales_daily_summary
            where store_id = :storeId
              and summary_date between :startDate and :endDate
            order by summary_date asc
            """,
        nativeQuery = true
    )
    List<SalesDailySummary> findAllByStore_idAndSummary_dateBetweenOrderBySummary_dateAsc(
        @Param("storeId") Long storeId,
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate
    );

    @Query(
        value = """
            select *
            from sales_daily_summary
            where organization_id = :organizationId
              and summary_date between :startDate and :endDate
            order by summary_date asc
            """,
        nativeQuery = true
    )
    List<SalesDailySummary> findAllByOrganization_idAndSummary_dateBetweenOrderBySummary_dateAsc(
        @Param("organizationId") Long organizationId,
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate
    );

    @Modifying
    @Query(
        value = """
            delete from sales_daily_summary
            where summary_date = :summaryDate
              and store_id = :storeId
            """,
        nativeQuery = true
    )
    void deleteAllBySummary_dateAndStore_id(@Param("summaryDate") LocalDate summaryDate, @Param("storeId") Long storeId);
}
