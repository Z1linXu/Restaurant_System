package com.restaurant.system.owner.provisioning.part2;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StoreLogicalPrinterRoleRepository extends JpaRepository<StoreLogicalPrinterRoleEntity, Long> {

    @Query("""
        select role from StoreLogicalPrinterRoleEntity role
        where role.store_id = :storeId order by role.role_code asc
        """)
    List<StoreLogicalPrinterRoleEntity> findAllByStoreIdOrderByRoleCodeAsc(@Param("storeId") Long storeId);

    @Query("""
        select role from StoreLogicalPrinterRoleEntity role
        where role.store_id = :storeId and role.role_code = :roleCode
        """)
    Optional<StoreLogicalPrinterRoleEntity> findByStoreIdAndRoleCode(
        @Param("storeId") Long storeId,
        @Param("roleCode") String roleCode
    );

    @Query("""
        select role from StoreLogicalPrinterRoleEntity role
        where role.store_id = :storeId and role.module_code = :moduleCode
        """)
    Optional<StoreLogicalPrinterRoleEntity> findByStoreIdAndModuleCode(
        @Param("storeId") Long storeId,
        @Param("moduleCode") String moduleCode
    );
}
