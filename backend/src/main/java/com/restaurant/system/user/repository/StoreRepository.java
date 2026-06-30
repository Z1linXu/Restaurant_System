package com.restaurant.system.user.repository;

import com.restaurant.system.user.entity.Store;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StoreRepository extends JpaRepository<Store, Long> {

    List<Store> findAllByStatusIgnoreCase(String status);

    @Query("select s from Store s where s.organization_id = :organizationId order by s.id asc")
    List<Store> findAllByOrganizationIdOrderByIdAsc(@Param("organizationId") Long organizationId);
}
