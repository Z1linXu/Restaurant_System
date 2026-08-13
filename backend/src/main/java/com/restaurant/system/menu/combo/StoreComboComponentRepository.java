package com.restaurant.system.menu.combo;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StoreComboComponentRepository extends JpaRepository<StoreComboComponent, Long> {

    @Query("""
        select component from StoreComboComponent component
        where component.store_id = :storeId
        order by component.component_group asc, component.display_order asc, component.id asc
        """)
    List<StoreComboComponent> findAllByStoreIdOrdered(@Param("storeId") Long storeId);

    @Query("""
        select component from StoreComboComponent component
        where component.store_id = :storeId
          and component.component_group = :componentGroup
          and component.component_code = :componentCode
        """)
    Optional<StoreComboComponent> findByStoreIdAndGroupAndCode(
        @Param("storeId") Long storeId,
        @Param("componentGroup") String componentGroup,
        @Param("componentCode") String componentCode
    );
}
