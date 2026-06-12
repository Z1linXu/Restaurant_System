package com.restaurant.system.analytics.repository;

import com.restaurant.system.analytics.entity.MenuItemSalesSummary;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MenuItemSalesSummaryRepository extends JpaRepository<MenuItemSalesSummary, Long> {
    @Query(
        value = """
            select *
            from menu_item_sales_summary
            where store_id = :storeId
              and summary_date between :startDate and :endDate
            order by sales_amount desc, quantity_sold desc, id asc
            """,
        nativeQuery = true
    )
    List<MenuItemSalesSummary> findAllByStore_idAndSummary_dateBetween(
        @Param("storeId") Long storeId,
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate
    );

    @Query(
        value = """
            select *
            from menu_item_sales_summary
            where organization_id = :organizationId
              and summary_date between :startDate and :endDate
            order by sales_amount desc, quantity_sold desc, id asc
            """,
        nativeQuery = true
    )
    List<MenuItemSalesSummary> findAllByOrganization_idAndSummary_dateBetween(
        @Param("organizationId") Long organizationId,
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate
    );

    @Modifying
    @Query(
        value = """
            delete from menu_item_sales_summary
            where summary_date = :summaryDate
              and store_id = :storeId
            """,
        nativeQuery = true
    )
    void deleteAllBySummary_dateAndStore_id(@Param("summaryDate") LocalDate summaryDate, @Param("storeId") Long storeId);
}
