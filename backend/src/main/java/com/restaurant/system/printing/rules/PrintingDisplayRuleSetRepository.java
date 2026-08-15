package com.restaurant.system.printing.rules;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

public interface PrintingDisplayRuleSetRepository extends JpaRepository<PrintingDisplayRuleSet, Long> {

    @Query("select ruleSet from PrintingDisplayRuleSet ruleSet where ruleSet.store_id = :storeId")
    Optional<PrintingDisplayRuleSet> findByStoreId(@Param("storeId") Long storeId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select ruleSet from PrintingDisplayRuleSet ruleSet where ruleSet.store_id = :storeId")
    Optional<PrintingDisplayRuleSet> findByStoreIdForUpdate(@Param("storeId") Long storeId);
}
