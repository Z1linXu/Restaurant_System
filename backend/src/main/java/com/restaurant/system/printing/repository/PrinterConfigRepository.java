package com.restaurant.system.printing.repository;

import com.restaurant.system.printing.entity.PrinterConfig;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PrinterConfigRepository extends JpaRepository<PrinterConfig, Long> {

    @Query("""
        select pc from PrinterConfig pc
        where pc.store_id = :storeId
        order by pc.id asc
        """)
    List<PrinterConfig> findAllByStoreIdOrderByIdAsc(@Param("storeId") Long storeId);
}
