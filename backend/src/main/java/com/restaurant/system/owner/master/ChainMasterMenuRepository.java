package com.restaurant.system.owner.master;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChainMasterMenuRepository extends JpaRepository<ChainMasterMenuEntity, Long> {

    @Query("""
        select menu from ChainMasterMenuEntity menu
        where menu.organization_id = :organizationId
          and menu.master_menu_key = :masterMenuKey
        """)
    Optional<ChainMasterMenuEntity> findByOrganizationAndKey(
        @Param("organizationId") Long organizationId,
        @Param("masterMenuKey") String masterMenuKey
    );
}
