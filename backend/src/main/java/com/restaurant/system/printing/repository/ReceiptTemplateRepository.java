package com.restaurant.system.printing.repository;

import com.restaurant.system.printing.entity.ReceiptTemplate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReceiptTemplateRepository extends JpaRepository<ReceiptTemplate, Long> {

    @Query("""
        select rt from ReceiptTemplate rt
        where rt.store_id = :storeId
        order by rt.id asc
        """)
    List<ReceiptTemplate> findAllByStoreIdOrderByIdAsc(@Param("storeId") Long storeId);
}
