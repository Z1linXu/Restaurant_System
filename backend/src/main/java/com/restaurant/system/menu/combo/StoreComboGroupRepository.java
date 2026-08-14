package com.restaurant.system.menu.combo;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface StoreComboGroupRepository extends JpaRepository<StoreComboGroup, Long> {

    @Query("""
        select comboGroup from StoreComboGroup comboGroup
        where comboGroup.store_id = :storeId
          and comboGroup.archived_at is null
        order by comboGroup.display_order asc, comboGroup.id asc
        """)
    List<StoreComboGroup> findAllByStoreIdOrdered(@Param("storeId") Long storeId);

    @Query("""
        select comboGroup from StoreComboGroup comboGroup
        where comboGroup.store_id = :storeId
        order by comboGroup.display_order asc, comboGroup.id asc
        """)
    List<StoreComboGroup> findAllByStoreIdIncludingArchivedOrdered(@Param("storeId") Long storeId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        select comboGroup from StoreComboGroup comboGroup
        where comboGroup.store_id = :storeId
          and comboGroup.archived_at is null
        order by comboGroup.display_order asc, comboGroup.id asc
        """)
    List<StoreComboGroup> findAllByStoreIdForUpdateOrdered(@Param("storeId") Long storeId);

    @Query("""
        select comboGroup from StoreComboGroup comboGroup
        where comboGroup.store_id = :storeId and comboGroup.group_code = :groupCode
          and comboGroup.archived_at is null
        """)
    Optional<StoreComboGroup> findByStoreIdAndGroupCode(
        @Param("storeId") Long storeId,
        @Param("groupCode") String groupCode
    );
}
