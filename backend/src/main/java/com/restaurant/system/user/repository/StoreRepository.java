package com.restaurant.system.user.repository;

import com.restaurant.system.user.entity.Store;
import java.util.List;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface StoreRepository extends JpaRepository<Store, Long> {

    List<Store> findAllByStatusIgnoreCase(String status);

    @Query("select s from Store s where s.organization_id = :organizationId order by s.id asc")
    List<Store> findAllByOrganizationIdOrderByIdAsc(@Param("organizationId") Long organizationId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query(value = """
        update stores
        set menu_revision = coalesce(menu_revision, 0) + 1,
            menu_updated_at = current_timestamp,
            updated_at = current_timestamp
        where id = :storeId
        """, nativeQuery = true)
    int incrementMenuRevision(@Param("storeId") Long storeId);
}
