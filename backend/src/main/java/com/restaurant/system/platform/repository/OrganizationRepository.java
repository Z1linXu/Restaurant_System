package com.restaurant.system.platform.repository;

import com.restaurant.system.platform.entity.Organization;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrganizationRepository extends JpaRepository<Organization, Long> {
    Organization findByCode(String code);

    Optional<Organization> findFirstByCodeIgnoreCase(String code);

    List<Organization> findAllByOrderByIdAsc();
}
