package com.restaurant.system.user.repository;

import com.restaurant.system.user.entity.Store;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface StoreRepository extends JpaRepository<Store, Long> {

    List<Store> findAllByStatusIgnoreCase(String status);

    List<Store> findAllByCodeIgnoreCase(String code);

    @Query("select s.menu_revision from Store s where s.id = :storeId")
    Long findMenuRevisionById(@Param("storeId") Long storeId);

    @Query("select s from Store s where s.organization_id = :organizationId order by s.id asc")
    List<Store> findAllByOrganizationIdOrderByIdAsc(@Param("organizationId") Long organizationId);

    @Query("""
        select s from Store s
        where s.organization_id = :organizationId
          and lower(s.code) = lower(:code)
        order by s.id asc
        """)
    List<Store> findAllByOrganizationIdAndCodeIgnoreCase(
        @Param("organizationId") Long organizationId,
        @Param("code") String code
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Store s where s.id in :storeIds order by s.id asc")
    List<Store> findAllByIdInForUpdateOrderByIdAsc(@Param("storeIds") Collection<Long> storeIds);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Store s where s.id = :storeId")
    java.util.Optional<Store> findByIdForUpdate(@Param("storeId") Long storeId);

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
