package com.restaurant.system.analytics.repository;

import com.restaurant.system.analytics.entity.AnalyticsAlert;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AnalyticsAlertRepository extends JpaRepository<AnalyticsAlert, Long> {
    @Query(
        value = """
            select *
            from analytics_alerts
            where store_id = :storeId
              and created_at between :start and :end
              and is_resolved = false
            order by created_at desc
            """,
        nativeQuery = true
    )
    List<AnalyticsAlert> findAllByStore_idAndCreated_atBetweenAndIs_resolvedFalseOrderByCreated_atDesc(
        @Param("storeId") Long storeId,
        @Param("start") LocalDateTime start,
        @Param("end") LocalDateTime end
    );

    @Query(
        value = """
            select *
            from analytics_alerts
            where organization_id = :organizationId
              and created_at between :start and :end
              and is_resolved = false
            order by created_at desc
            """,
        nativeQuery = true
    )
    List<AnalyticsAlert> findAllByOrganization_idAndCreated_atBetweenAndIs_resolvedFalseOrderByCreated_atDesc(
        @Param("organizationId") Long organizationId,
        @Param("start") LocalDateTime start,
        @Param("end") LocalDateTime end
    );

    @Modifying
    @Query(
        value = """
            delete from analytics_alerts
            where store_id = :storeId
              and created_at between :start and :end
            """,
        nativeQuery = true
    )
    void deleteAllByStore_idAndCreated_atBetween(
        @Param("storeId") Long storeId,
        @Param("start") LocalDateTime start,
        @Param("end") LocalDateTime end
    );
}
