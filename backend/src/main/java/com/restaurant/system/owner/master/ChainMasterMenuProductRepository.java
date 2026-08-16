package com.restaurant.system.owner.master;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChainMasterMenuProductRepository extends JpaRepository<ChainMasterMenuProductEntity, Long> {

    @Query("""
        select product from ChainMasterMenuProductEntity product
        where product.master_menu_version_id = :masterMenuVersionId
        order by product.sort_order asc, product.id asc
        """)
    List<ChainMasterMenuProductEntity> findAllByVersionOrdered(
        @Param("masterMenuVersionId") Long masterMenuVersionId
    );
}
