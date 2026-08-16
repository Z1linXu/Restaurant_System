package com.restaurant.system.owner.provisioning;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StoreMenuMasterMappingRepository extends JpaRepository<StoreMenuMasterMappingEntity, Long> {

    @Query("""
        select mapping from StoreMenuMasterMappingEntity mapping
        where mapping.store_id = :storeId
          and mapping.master_menu_version_id = :masterMenuVersionId
        order by mapping.entity_type asc, mapping.local_entity_id asc
        """)
    List<StoreMenuMasterMappingEntity> findAllByStoreAndMasterVersion(
        @Param("storeId") Long storeId,
        @Param("masterMenuVersionId") Long masterMenuVersionId
    );
}
