package com.restaurant.system.owner.provisioning.part2;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StoreProvisioningResourceRepository extends JpaRepository<StoreProvisioningResourceEntity, Long> {

    @Query("""
        select resource from StoreProvisioningResourceEntity resource
        where resource.store_id = :storeId
          and resource.resource_type = :resourceType
          and resource.resource_code = :resourceCode
        """)
    Optional<StoreProvisioningResourceEntity> findByStoreIdAndResourceTypeAndResourceCode(
        @Param("storeId") Long storeId,
        @Param("resourceType") String resourceType,
        @Param("resourceCode") String resourceCode
    );

    @Query("""
        select resource from StoreProvisioningResourceEntity resource
        where resource.store_id = :storeId and resource.resource_type = :resourceType
        order by resource.resource_code asc
        """)
    List<StoreProvisioningResourceEntity> findAllByStoreIdAndResourceTypeOrderByResourceCodeAsc(
        @Param("storeId") Long storeId,
        @Param("resourceType") String resourceType
    );
}
