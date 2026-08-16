package com.restaurant.system.owner.master;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChainMasterMenuCategoryRepository extends JpaRepository<ChainMasterMenuCategoryEntity, Long> {

    @Query("""
        select category from ChainMasterMenuCategoryEntity category
        where category.master_menu_version_id = :masterMenuVersionId
        order by category.sort_order asc, category.id asc
        """)
    List<ChainMasterMenuCategoryEntity> findAllByVersionOrdered(
        @Param("masterMenuVersionId") Long masterMenuVersionId
    );
}
