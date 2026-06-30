package com.restaurant.system.audit.repository;

import com.restaurant.system.audit.entity.AuditLog;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    @Query("select a from AuditLog a where a.store_id = :storeId and a.created_at >= :start and a.created_at < :end order by a.created_at desc")
    List<AuditLog> findAllByStore_idAndCreated_atBetweenOrderByCreated_atDesc(
        @Param("storeId") Long storeId,
        @Param("start") LocalDateTime start,
        @Param("end") LocalDateTime end
    );

    @Query("select a from AuditLog a where a.created_at >= :start and a.created_at < :end order by a.created_at desc")
    List<AuditLog> findAllByCreated_atBetweenOrderByCreated_atDesc(
        @Param("start") LocalDateTime start,
        @Param("end") LocalDateTime end
    );
}
