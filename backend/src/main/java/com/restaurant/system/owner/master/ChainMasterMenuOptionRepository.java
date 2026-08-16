package com.restaurant.system.owner.master;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChainMasterMenuOptionRepository extends JpaRepository<ChainMasterMenuOptionEntity, Long> {

    @Query("""
        select optionEntity from ChainMasterMenuOptionEntity optionEntity
        where optionEntity.master_menu_version_id = :masterMenuVersionId
        order by optionEntity.sort_order asc, optionEntity.id asc
        """)
    List<ChainMasterMenuOptionEntity> findAllByVersionOrdered(
        @Param("masterMenuVersionId") Long masterMenuVersionId
    );
}
