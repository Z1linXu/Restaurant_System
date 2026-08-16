package com.restaurant.system.owner.master;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChainMasterMenuVersionRepository extends JpaRepository<ChainMasterMenuVersionEntity, Long> {

    @Query("""
        select version from ChainMasterMenuVersionEntity version
        where version.master_menu_id = :masterMenuId
          and version.version_key = :versionKey
        """)
    Optional<ChainMasterMenuVersionEntity> findByMasterMenuAndVersionKey(
        @Param("masterMenuId") Long masterMenuId,
        @Param("versionKey") String versionKey
    );
}
