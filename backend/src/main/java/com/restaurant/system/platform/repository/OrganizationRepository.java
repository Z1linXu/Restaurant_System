package com.restaurant.system.platform.repository;

import com.restaurant.system.platform.entity.Organization;
import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrganizationRepository extends JpaRepository<Organization, Long> {
    Organization findByCode(String code);

    Optional<Organization> findFirstByCodeIgnoreCase(String code);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select organization from Organization organization where organization.id = :organizationId")
    Optional<Organization> findByIdForUpdate(@Param("organizationId") Long organizationId);

    List<Organization> findAllByOrderByIdAsc();
}
