package com.restaurant.system.modules;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StoreModuleRepository extends JpaRepository<StoreModule, Long> {

    @Query("""
        select sm from StoreModule sm
        where sm.store_id = :storeId
        order by sm.id asc
        """)
    List<StoreModule> findAllByStoreIdOrderByIdAsc(@Param("storeId") Long storeId);

    @Query("""
        select sm from StoreModule sm
        where sm.store_id = :storeId and sm.module_key = :moduleKey
        """)
    Optional<StoreModule> findByStoreIdAndModuleKey(
        @Param("storeId") Long storeId,
        @Param("moduleKey") String moduleKey
    );

    @Query("""
        select sm from StoreModule sm
        where sm.store_id = :storeId and sm.module_key in :moduleKeys
        order by sm.id asc
        """)
    List<StoreModule> findAllByStoreIdAndModuleKeyIn(
        @Param("storeId") Long storeId,
        @Param("moduleKeys") Collection<String> moduleKeys
    );
}
